package com.huawei.ascend.bus.gateway.runtime;

import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerControlDescriptor;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerForwardingConsumerPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerForwardingRelayPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerInboundMessage;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxClaimPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.InvocationResponseStatus;
import com.huawei.ascend.bus.spi.ingress.IngressEnvelope;
import com.huawei.ascend.bus.spi.ingress.IngressGateway;
import com.huawei.ascend.bus.spi.ingress.IngressResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Production gateway runtime for FEAT-013 client-invocation event forwarding
 * (S2 of REQ-2026-001 step-6 W2).
 *
 * <p>Implements {@link IngressGateway#routeClientRequest}: it builds a
 * {@link ForwardingEnvelope} carrying the request's control descriptor in its
 * {@code payloadRef}, dispatches it through the outbox / claim / relay substrate
 * (synchronous simplified produce — the real async worker loop is S4), then polls
 * the response consumer within the accept window and classifies responses by the
 * NATIVE {@link BrokerInboundMessage#eventType()} (matched by the NATIVE
 * {@link BrokerInboundMessage#correlationId()}) — NO descriptor-decoding for
 * classification (L2 feat-013 §4.2 / §4.3).
 *
 * <p><b>Accept-window state machine (§4.3).</b> The gateway observes, per
 * {@code correlationId == requestId}:
 * <ul>
 *   <li>{@code INVOCATION_ACCEPTED} → records {@code taskId}, keeps polling for a
 *       terminal up to {@code responseTimeoutMs}; if the window drains with no
 *       terminal, returns {@link IngressResponse#accepted ACCEPTED} with the
 *       {@code taskId} cursor ({@link InvocationResponseStatus#ACCEPTED_WITH_TASK}).</li>
 *   <li>{@code INVOCATION_RESPONSE} → {@link InvocationResponseStatus#COMPLETED_RESPONSE}
 *       → returns {@code ACCEPTED} (cursor = {@code taskId}).</li>
 *   <li>{@code INVOCATION_STREAM_READY} → {@link InvocationResponseStatus#STREAM_READY}
 *       → returns {@code ACCEPTED} (cursor = {@code streamRef}). SSE bridging itself
 *       is deferred to S4 (S2 returns the stream reference cursor only).</li>
 *   <li>{@code INVOCATION_REJECTED} / {@code INVOCATION_FAILED} →
 *       {@link IngressResponse#rejected REJECTED}.</li>
 *   <li>{@code INVOCATION_TERMINAL} with {@code status=completed} →
 *       {@link InvocationResponseStatus#COMPLETED_RESPONSE}; else {@code FAILED}.</li>
 *   <li>No accepted/terminal within the window → {@link IngressResponse#deferred
 *       DEFERRED} (the §6.2.3 UNKNOWN outcome — the client retries with the same
 *       {@code idempotencyKey}).</li>
 * </ul>
 *
 * <p><b>Self-consumption.</b> The gateway's own request (whose
 * {@code sourceServiceId} equals this gateway's {@code sourceServiceId}) is visible
 * to the gateway's consumer-group; {@link #acceptWindow} commits it without
 * processing and keeps polling for real responses.
 *
 * <p><b>Deferred to S4.</b> The Spring HTTP controller ({@code GatewayRuntimeController},
 * {@code POST /a2a}), the real async dispatch worker, and the full A2A SSE bridge
 * (token-streaming) are NOT in S2 — this class is the {@link IngressGateway} impl
 * with {@link #dispatchRequest} + {@link #acceptWindow} exposed as testable sub-methods.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §4.2 / §4.3 / §4.6 / §6}.
 */
// scope: gateway runtime — IngressGateway impl; pure Java (controller/SSE-bridge deferred to S4)
public final class GatewayRuntimeService implements IngressGateway {

    /** Lease duration granted when the gateway claims its own outbox record (matches the test double). */
    static final long DISPATCH_LEASE_MS = 60_000L;

    private final ForwardingOutboxPort outbox;
    private final ForwardingOutboxClaimPort claimPort;
    private final BrokerForwardingRelayPort relay;
    private final BrokerForwardingConsumerPort responseConsumer;
    private final String sourceServiceId;
    private final String consumerServiceId;
    private final long acceptTimeoutMs;
    private final long responseTimeoutMs;
    private final LongSupplier clock;

    /**
     * @param outbox             the gateway's durable outbox (enqueue + ack)
     * @param claimPort          the gateway's claim / lease port
     * @param relay              the broker relay (produce the request onto the broker)
     * @param responseConsumer  the broker consumer (poll responses off the broker)
     * @param sourceServiceId    the gateway's service id (envelope sourceServiceId + claim leaseOwner)
     * @param consumerServiceId the gateway's broker consumer-group id (response poll)
     * @param acceptTimeoutMs    accept window: no accepted/rejected/failed within → UNKNOWN (deferred)
     * @param responseTimeoutMs  post-accepted window: no terminal within → ACCEPTED_WITH_TASK
     * @param clock              epoch-millis supplier (drives due / lease / window judgements)
     */
    public GatewayRuntimeService(ForwardingOutboxPort outbox,
                                 ForwardingOutboxClaimPort claimPort,
                                 BrokerForwardingRelayPort relay,
                                 BrokerForwardingConsumerPort responseConsumer,
                                 String sourceServiceId,
                                 String consumerServiceId,
                                 long acceptTimeoutMs,
                                 long responseTimeoutMs,
                                 LongSupplier clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox is required");
        this.claimPort = Objects.requireNonNull(claimPort, "claimPort is required");
        this.relay = Objects.requireNonNull(relay, "relay is required");
        this.responseConsumer = Objects.requireNonNull(responseConsumer, "responseConsumer is required");
        this.sourceServiceId = requireNonBlank(sourceServiceId, "sourceServiceId");
        this.consumerServiceId = requireNonBlank(consumerServiceId, "consumerServiceId");
        if (acceptTimeoutMs < 0) {
            throw new IllegalArgumentException("acceptTimeoutMs must be >= 0");
        }
        if (responseTimeoutMs < 0) {
            throw new IllegalArgumentException("responseTimeoutMs must be >= 0");
        }
        this.acceptTimeoutMs = acceptTimeoutMs;
        this.responseTimeoutMs = responseTimeoutMs;
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    // ===== IngressGateway =====

    @Override
    public IngressResponse routeClientRequest(IngressEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope is required");
        dispatchRequest(envelope);
        return acceptWindow(envelope.requestId(), envelope.tenantId());
    }

    // ===== dispatch (request → outbox → broker) =====

    /**
     * Build the request {@link ForwardingEnvelope} and synchronously dispatch it:
     * enqueue → claimDue → {@link BrokerForwardingRelayPort#produce produce} → markAcked.
     * Returns the built envelope so tests can assert the mapped eventType / correlationId /
     * payloadRef descriptor.
     *
     * <p>S2 simplified synchronous produce; the real async worker loop is S4.
     */
    public ForwardingEnvelope dispatchRequest(IngressEnvelope env) {
        Objects.requireNonNull(env, "env is required");
        long now = clock.getAsLong();
        AgentBusEventType eventType = mapEventType(env.requestType());
        ForwardingRouteHandle routeHandle = resolveRouteHandle(env);
        String targetServiceId = resolveTargetServiceId(env);
        String capability = resolveCapability(env);
        long deadline = env.deadlineMillisEpoch() != null ? env.deadlineMillisEpoch() : Long.MAX_VALUE;
        String correlationId = env.requestId().toString();
        String idempotencyKey = env.idempotencyKey().toString();

        String descriptor = BrokerControlDescriptor.encode(
                eventType, env.traceId(), correlationId, idempotencyKey,
                routeHandle.value(), capability, deadline);
        ForwardingEnvelope envelope = new ForwardingEnvelope(
                new ForwardingMessageId("gw-" + UUID.randomUUID()),
                eventType,
                env.tenantId(),
                env.traceId(),
                correlationId,
                idempotencyKey,
                routeHandle,
                capability,
                sourceServiceId,
                targetServiceId,
                deadline,
                ForwardingEnvelope.PayloadPolicy.DATA_BEARING,
                descriptor);

        // enqueue → claim → produce → markAcked (simplified synchronous dispatch; S4 adds the worker loop)
        ForwardingReceipt receipt = outbox.enqueue(envelope, sourceServiceId, targetServiceId, now);
        Objects.requireNonNull(receipt, "enqueue receipt");
        List<ForwardingOutboxRecord> claimed = claimPort.claimDue(
                env.tenantId(), now, 1, sourceServiceId, now + DISPATCH_LEASE_MS);
        for (ForwardingOutboxRecord record : claimed) {
            relay.produce(record, now);
            outbox.markAcked(record.messageId(), env.tenantId(), sourceServiceId);
        }
        return envelope;
    }

    // ===== accept window (poll responses, classify, return IngressResponse) =====

    /**
     * Poll the response consumer within the accept window, classify each matching
     * response by its NATIVE {@link BrokerInboundMessage#eventType()}, and return the
     * resulting {@link IngressResponse}. Skips (commits) the gateway's own request
     * (self-consumption) and any non-matching responses.
     *
     * @param requestId the ingress request id (correlationId == requestId.toString())
     * @param tenantId  tenant scope for the response poll (Rule R-C.c)
     * @return the observed acknowledgement; never null
     */
    public IngressResponse acceptWindow(UUID requestId, String tenantId) {
        Objects.requireNonNull(requestId, "requestId is required");
        requireNonBlank(tenantId, "tenantId");
        String correlationId = requestId.toString();
        long start = clock.getAsLong();
        long acceptDeadline = start + acceptTimeoutMs;
        long responseDeadline = start + responseTimeoutMs;

        String taskId = null;
        while (true) {
            long now = clock.getAsLong();
            BrokerInboundMessage msg = responseConsumer.poll(consumerServiceId, tenantId, now).orElse(null);
            if (msg == null) {
                break; // no further responses available in this synchronous window
            }
            // self-consumption: the gateway's own request (source == this gateway) → commit + keep polling
            if (sourceServiceId.equals(msg.sourceServiceId())) {
                responseConsumer.commit(msg);
                continue;
            }
            // a response for a different request → commit (advance) and keep polling for ours
            if (!correlationId.equals(msg.correlationId())) {
                responseConsumer.commit(msg);
                continue;
            }
            InvocationResponseStatus status = classify(msg);
            responseConsumer.commit(msg);
            switch (status) {
                case ACCEPTED_WITH_TASK -> {
                    taskId = BrokerControlDescriptor.token(msg.payloadRef(), "taskId");
                    if (now >= responseDeadline) {
                        return toIngressResponse(requestId, status, taskId, null, null);
                    }
                    // keep polling for a terminal up to responseTimeout
                }
                case COMPLETED_RESPONSE -> {
                    String cursor = taskId != null ? taskId
                            : BrokerControlDescriptor.token(msg.payloadRef(), "taskId");
                    return toIngressResponse(requestId, status, cursor, null, null);
                }
                case STREAM_READY -> {
                    String streamRef = BrokerControlDescriptor.token(msg.payloadRef(), "streamRef");
                    return toIngressResponse(requestId, status, null, streamRef, null);
                }
                case REJECTED, FAILED -> {
                    String reason = reasonFrom(msg, status);
                    return toIngressResponse(requestId, status, null, null, reason);
                }
                case UNKNOWN -> {
                    // defensive: classify should not yield UNKNOWN for a known eventType; keep polling
                }
            }
            // guard: accept window exhausted with no accepted → break to UNKNOWN
            if (taskId == null && now >= acceptDeadline) {
                break;
            }
        }
        // window drained without a terminal: ACCEPTED_WITH_TASK if we got a taskId, else UNKNOWN (deferred)
        if (taskId != null) {
            return toIngressResponse(requestId, InvocationResponseStatus.ACCEPTED_WITH_TASK, taskId, null, null);
        }
        return toIngressResponse(requestId, InvocationResponseStatus.UNKNOWN, null, null, null);
    }

    // ===== classification (by NATIVE eventType — no descriptor decoding) =====

    /**
     * Classify a polled response by its NATIVE {@link BrokerInboundMessage#eventType()}
     * (FEAT-013/014 family). The {@code INVOCATION_TERMINAL} / {@code A2A_CALL_TERMINAL}
     * sub-state (completed vs failed) is decoded from the {@code status=} token in the
     * payloadRef; all other classifications are pure eventType mappings.
     *
     * @param m the polled response (non-null; correlationId already matched by the caller)
     * @return the observed invocation status
     */
    public InvocationResponseStatus classify(BrokerInboundMessage m) {
        Objects.requireNonNull(m, "m is required");
        AgentBusEventType eventType = m.eventType();
        if (eventType == null) {
            return InvocationResponseStatus.UNKNOWN; // control-only / JDBC-back-compat
        }
        return switch (eventType) {
            case INVOCATION_RESPONSE, A2A_CALL_RESPONSE -> InvocationResponseStatus.COMPLETED_RESPONSE;
            case INVOCATION_ACCEPTED, A2A_CALL_ACCEPTED -> InvocationResponseStatus.ACCEPTED_WITH_TASK;
            case INVOCATION_STREAM_READY, A2A_STREAM_READY -> InvocationResponseStatus.STREAM_READY;
            case INVOCATION_REJECTED, A2A_CALL_REJECTED -> InvocationResponseStatus.REJECTED;
            case INVOCATION_FAILED, A2A_CALL_FAILED -> InvocationResponseStatus.FAILED;
            case INVOCATION_TERMINAL, A2A_CALL_TERMINAL -> {
                String status = BrokerControlDescriptor.token(m.payloadRef(), "status");
                yield "completed".equals(status)
                        ? InvocationResponseStatus.COMPLETED_RESPONSE
                        : InvocationResponseStatus.FAILED;
            }
            // request eventTypes should not appear as responses; treat as unknown (be defensive)
            default -> InvocationResponseStatus.UNKNOWN;
        };
    }

    // ===== IngressResponse mapping =====

    /**
     * Map an observed invocation status to an {@link IngressResponse}.
     *
     * @param requestId the ingress request id (mirrored on the response)
     * @param status   the observed invocation status
     * @param taskId   cursor for COMPLETED_RESPONSE / ACCEPTED_WITH_TASK (nullable for COMPLETED_RESPONSE)
     * @param streamRef cursor for STREAM_READY
     * @param reason   rejection reason for REJECTED / FAILED (nullable → defaulted from status)
     * @return the bus acknowledgement; never null
     */
    public IngressResponse toIngressResponse(UUID requestId, InvocationResponseStatus status,
                                             String taskId, String streamRef, String reason) {
        Objects.requireNonNull(requestId, "requestId is required");
        Objects.requireNonNull(status, "status is required");
        return switch (status) {
            case COMPLETED_RESPONSE -> IngressResponse.accepted(requestId, taskId); // cursor = taskId or null
            case ACCEPTED_WITH_TASK -> IngressResponse.accepted(requestId, taskId);
            case STREAM_READY -> IngressResponse.accepted(requestId, streamRef);
            case REJECTED, FAILED -> IngressResponse.rejected(requestId, rejectionReason(status, reason));
            case UNKNOWN -> IngressResponse.deferred(requestId);
        };
    }

    // ===== requestType → eventType mapping (§4.2 envelope encapsulation) =====

    private static AgentBusEventType mapEventType(IngressEnvelope.IngressRequestType requestType) {
        return switch (requestType) {
            case RUN_CREATE -> AgentBusEventType.CLIENT_INVOCATION_REQUESTED;
            case RUN_CANCEL -> AgentBusEventType.CLIENT_INVOCATION_CANCEL_REQUESTED;
            case RUN_GET -> AgentBusEventType.CLIENT_INVOCATION_QUERY_REQUESTED;
            case RUN_RESUME -> AgentBusEventType.CLIENT_STREAM_SUBSCRIBE_REQUESTED;
        };
    }

    // ===== request attribute resolution (S2 seam; S4 controller resolves via registry) =====

    private static ForwardingRouteHandle resolveRouteHandle(IngressEnvelope env) {
        String value = requireStringAttribute(env.requestAttributes(), "routeHandle");
        return new ForwardingRouteHandle(value, env.tenantId());
    }

    private static String resolveTargetServiceId(IngressEnvelope env) {
        return requireStringAttribute(env.requestAttributes(), "targetServiceId");
    }

    private static String resolveCapability(IngressEnvelope env) {
        Object v = env.requestAttributes().get("capability");
        if (v == null) {
            return "a2a"; // default capability when omitted
        }
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(
                    "requestAttributes 'capability' must be a non-blank String");
        }
        return s;
    }

    private static String requireStringAttribute(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(
                    "requestAttributes must carry a non-blank String '" + key + "'");
        }
        return s;
    }

    // ===== helpers =====

    private static String reasonFrom(BrokerInboundMessage m, InvocationResponseStatus status) {
        String reason = BrokerControlDescriptor.token(m.payloadRef(), "reason");
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        String tokenStatus = BrokerControlDescriptor.token(m.payloadRef(), "status");
        if (tokenStatus != null && !tokenStatus.isBlank()) {
            return tokenStatus;
        }
        return m.eventType() != null ? m.eventType().name() : status.name();
    }

    private static String rejectionReason(InvocationResponseStatus status, String reason) {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        return status.name();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
