package com.huawei.ascend.bus.forwarding.runtime.relay;

import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerControlDescriptor;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerForwardingConsumerPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerForwardingRelayPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerInboundMessage;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerProduceOutcome;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingInboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxClaimPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Event-bus two-hop governance relay worker (FEAT-013/014, arch-driven to-be slice A).
 *
 * <p>The governance relay between the two broker hops (L2 feat-013 §1.2 / §4.1): it
 * CONSUMES a hop1 request event off the broker, applies between-hops governance
 * (inbox dedup + correlation-match + audit), and RE-PUBLISHES it as hop2 onto the
 * deliver topic, then commits (model B ack-after-consume). This is the
 * consume→govern→re-produce shape the as-is plane lacks — the existing
 * {@code ForwardingDispatcherWorker} is a producer-shape claim-own-outbox→deliver
 * worker, not a broker-consume→re-publish relay.
 *
 * <p><b>Routing-agnostic and direction-agnostic.</b> The worker produces via the
 * injected {@link BrokerForwardingRelayPort}, whose {@code ForwardingEndpointResolver}
 * is wired per relay role (forward relay → req / deliver topics; response relay →
 * resp_in / resp_out topics). The 8-topic distinctness is a wiring concern (Spring
 * profile configs, slice B), not a worker-logic concern. Which eventTypes the relay
 * re-publishes is likewise configured, not hard-coded: a forward relay is constructed
 * with {@link #FORWARD_REQUEST_TYPES} (hop1 req → hop2 deliver); a response relay with
 * {@link #RESPONSE_TYPES} (resp_in → resp_out, symmetric governance — the response
 * carries a {@link BrokerControlDescriptor} so the same decode / correlation-match /
 * dedup / audit applies). The worker just re-publishes the forwarded record through
 * whatever relay port + type set it is constructed with.
 *
 * <p><b>Governance acts applied between the hops:</b>
 * <ul>
 *   <li><b>skip non-relayable</b> — self-produced echoes / control / out-of-direction
 *       messages (soft eventType absent or not in this relay's configured type set)
 *       are committed without processing, mirroring {@code TestAgentRuntime.pollAndProcess}.
 *       A forward relay is constructed with {@link #FORWARD_REQUEST_TYPES}; a response
 *       relay with {@link #RESPONSE_TYPES}.</li>
 *   <li><b>descriptor decode</b> — {@link BrokerControlDescriptor#decode} recovers the
 *       request's control fields (eventType/traceId/correlationId/idempotencyKey/
 *       routeHandle/capability/deadline) from the {@code payloadRef} (the only field
 *       besides the native correlation/event headers that survives the broker poll).
 *       A malformed descriptor → commit + inbox REJECTED (non-retryable; a broker
 *       redelivery cannot fix a poison message, so it is not redelivered).</li>
 *   <li><b>correlation match</b> — the native header {@code correlationId} must equal
 *       the descriptor's {@code correlationId} (the gateway stamped both consistently).
 *       A mismatch → commit + inbox REJECTED (data-integrity failure, non-retryable).</li>
 *   <li><b>inbox dedup</b> — {@link ForwardingInboxPort#receive} returns
 *       {@link ForwardingStatus.Inbox#DUPLICATE_SUPPRESSED} on a replayed
 *       {@code (tenantId, messageId, consumerServiceId)}; the relay commits without
 *       re-publishing, so an at-least-once redelivery of hop1 never double-produces
 *       hop2.</li>
 *   <li><b>audit</b> — {@link ForwardingInboxPort#markConsumed} (success) /
 *       {@link ForwardingInboxPort#markRejected} (governance failure) record the
 *       terminal inbox state for observability.</li>
 * </ul>
 *
 * <p><b>Re-publish path</b> mirrors {@code GatewayRuntimeService.dispatchRequest} /
 * {@code TestAgentRuntime.produceResponse}: outbox.enqueue → claimDue(1) →
 * {@link BrokerForwardingRelayPort#produce} → markAcked. A produce
 * {@link BrokerProduceOutcome.Outcome#UNAVAILABLE UNAVAILABLE} / {@code ROUTE_NOT_FOUND}
 * → consumer.reject (redeliver; the agent-bus retry policy owns when).
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §1.2 / §4.1};
 * {@code docs/4plus1/delta/event-bus-relay/decision-tree.md} Q1a.
 */
// scope: forwarding.runtime.relay — event-bus two-hop governance relay worker; pure Java
public final class EventBusRelayWorker {

    /**
     * The FEAT-013/014 forward-request eventTypes a <b>forward relay</b> relays
     * (hop1 {@code ascend_bus_*_req} → hop2 {@code ascend_bus_*_deliver}). The
     * request carries a {@link BrokerControlDescriptor} in its {@code payloadRef}.
     */
    public static final Set<AgentBusEventType> FORWARD_REQUEST_TYPES = Set.of(
            AgentBusEventType.CLIENT_INVOCATION_REQUESTED,
            AgentBusEventType.CLIENT_INVOCATION_CANCEL_REQUESTED,
            AgentBusEventType.CLIENT_INVOCATION_QUERY_REQUESTED,
            AgentBusEventType.CLIENT_STREAM_SUBSCRIBE_REQUESTED,
            AgentBusEventType.A2A_CALL_REQUESTED,
            AgentBusEventType.A2A_CALL_CANCEL_REQUESTED,
            AgentBusEventType.A2A_CALL_QUERY_REQUESTED,
            AgentBusEventType.A2A_STREAM_SUBSCRIBE_REQUESTED);

    /**
     * The FEAT-013/014 response/terminal eventTypes a <b>response relay</b>
     * relays ({@code ascend_bus_*_resp_in} → {@code ascend_bus_*_resp_out}). The
     * response carries a {@link BrokerControlDescriptor} in its {@code payloadRef}
     * (symmetric to the request) so the relay recovers the control fields
     * (traceId / idempotencyKey / routeHandle / capability / deadline) lost crossing
     * the resp_in hop, plus {@code taskId} / {@code status} / {@code streamRef}
     * tokens the gateway reads via {@link BrokerControlDescriptor#token}.
     */
    public static final Set<AgentBusEventType> RESPONSE_TYPES = Set.of(
            AgentBusEventType.INVOCATION_ACCEPTED,
            AgentBusEventType.INVOCATION_REJECTED,
            AgentBusEventType.INVOCATION_FAILED,
            AgentBusEventType.INVOCATION_RESPONSE,
            AgentBusEventType.INVOCATION_STREAM_READY,
            AgentBusEventType.INVOCATION_TERMINAL,
            AgentBusEventType.A2A_CALL_ACCEPTED,
            AgentBusEventType.A2A_CALL_REJECTED,
            AgentBusEventType.A2A_CALL_FAILED,
            AgentBusEventType.A2A_CALL_RESPONSE,
            AgentBusEventType.A2A_STREAM_READY,
            AgentBusEventType.A2A_CALL_TERMINAL);

    private final BrokerForwardingConsumerPort consumer;
    private final ForwardingInboxPort inbox;
    private final ForwardingOutboxPort outbox;
    private final ForwardingOutboxClaimPort claimPort;
    private final BrokerForwardingRelayPort relay;
    private final String consumerServiceId;
    private final String sourceServiceId;
    private final long leaseDurationMillis;
    private final Set<AgentBusEventType> relayableTypes;

    public EventBusRelayWorker(BrokerForwardingConsumerPort consumer,
                                ForwardingInboxPort inbox,
                                ForwardingOutboxPort outbox,
                                ForwardingOutboxClaimPort claimPort,
                                BrokerForwardingRelayPort relay,
                                String consumerServiceId,
                                String sourceServiceId,
                                long leaseDurationMillis,
                                Set<AgentBusEventType> relayableTypes) {
        this.consumer = Objects.requireNonNull(consumer, "consumer is required");
        this.inbox = Objects.requireNonNull(inbox, "inbox is required");
        this.outbox = Objects.requireNonNull(outbox, "outbox is required");
        this.claimPort = Objects.requireNonNull(claimPort, "claimPort is required");
        this.relay = Objects.requireNonNull(relay, "relay is required");
        this.consumerServiceId = requireNonBlank(consumerServiceId, "consumerServiceId");
        this.sourceServiceId = requireNonBlank(sourceServiceId, "sourceServiceId");
        if (leaseDurationMillis <= 0) {
            throw new IllegalArgumentException("leaseDurationMillis must be > 0");
        }
        this.leaseDurationMillis = leaseDurationMillis;
        Objects.requireNonNull(relayableTypes, "relayableTypes is required");
        if (relayableTypes.isEmpty()) {
            throw new IllegalArgumentException("relayableTypes must not be empty");
        }
        this.relayableTypes = Set.copyOf(relayableTypes);
    }

    /**
     * One relay tick for a single tenant: poll up to {@code limit} hop-in messages
     * (hop1 requests for a forward relay, resp_in responses for a response relay),
     * apply between-hops governance, re-publish each as hop-out, and commit
     * (or reject for redelivery on a transient produce failure).
     *
     * @param tenantId      tenant scope (the consumer is subscribed tenant-scoped)
     * @param nowMillisEpoch the poll / produce instant
     * @param limit         max messages to relay this tick ({@code > 0})
     * @return the tick summary
     * @throws IllegalArgumentException if {@code tenantId} is blank or {@code limit <= 0}
     */
    public RelayTickResult runOnce(String tenantId, long nowMillisEpoch, int limit) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        int relayed = 0;
        int dedupSuppressed = 0;
        int governanceRejected = 0;
        int skipped = 0;

        for (int i = 0; i < limit; i++) {
            Optional<BrokerInboundMessage> polled = consumer.poll(nowMillisEpoch);
            if (polled.isEmpty()) {
                break; // no more hop1 messages this tick
            }
            BrokerInboundMessage msg = polled.get();

            // governance 1: skip non-relayable echoes / control / out-of-direction (soft eventType
            // absent or not in this relay's configured type set) — commit + advance.
            AgentBusEventType eventType = BrokerControlDescriptor.softEventType(msg.payloadRef());
            if (eventType == null || !relayableTypes.contains(eventType)) {
                consumer.commit(msg);
                skipped++;
                continue;
            }

            // governance 2: descriptor decode (recovers control fields lost crossing the hop).
            BrokerControlDescriptor.Descriptor desc;
            try {
                desc = BrokerControlDescriptor.decode(msg.payloadRef());
            } catch (RuntimeException ex) {
                rejectPoison(msg, ForwardingFailureCode.PAYLOAD_REF_INVALID);
                governanceRejected++;
                continue;
            }

            // governance 3: correlation match (native header corrId == descriptor corrId).
            if (!Objects.equals(msg.correlationId(), desc.correlationId())) {
                rejectPoison(msg, ForwardingFailureCode.PAYLOAD_REF_INVALID);
                governanceRejected++;
                continue;
            }

            // governance 4: inbox dedup (a replayed hop1 must not double-produce hop2).
            ForwardingEnvelope env = buildHop2Envelope(msg, desc);
            ForwardingStatus.Inbox inboxStatus = inbox.receive(env, consumerServiceId, nowMillisEpoch);
            if (inboxStatus == ForwardingStatus.Inbox.DUPLICATE_SUPPRESSED) {
                consumer.commit(msg);
                dedupSuppressed++;
                continue;
            }

            // re-publish: enqueue → claim → produce → markAcked (mirrors gateway dispatch).
            ForwardingReceipt receipt = outbox.enqueue(env, sourceServiceId, env.targetServiceId(), nowMillisEpoch);
            Objects.requireNonNull(receipt, "enqueue receipt");
            List<ForwardingOutboxRecord> claimed = claimPort.claimDue(tenantId, nowMillisEpoch, 1,
                    consumerServiceId, nowMillisEpoch + leaseDurationMillis);
            BrokerProduceOutcome outcome = null;
            for (ForwardingOutboxRecord record : claimed) {
                outcome = relay.produce(record, nowMillisEpoch);
                if (outcome.outcome() == BrokerProduceOutcome.Outcome.ACCEPTED) {
                    outbox.markAcked(record.messageId(), tenantId, consumerServiceId);
                }
            }

            if (outcome != null && outcome.outcome() == BrokerProduceOutcome.Outcome.ACCEPTED) {
                inbox.markConsumed(env.messageId(), tenantId, consumerServiceId);
                consumer.commit(msg);
                relayed++;
            } else {
                // produce UNAVAILABLE / ROUTE_NOT_FOUND (or nothing claimed): reject → redeliver.
                // The agent-bus retry policy owns when; ROUTE_NOT_FOUND redelivery still cannot fix
                // a bad route, but rejecting leaves the hop1 message visible for operator intervention.
                ForwardingFailureCode code = (outcome != null && outcome.failureCode() != null)
                        ? outcome.failureCode()
                        : ForwardingFailureCode.RECEIVER_UNAVAILABLE;
                consumer.reject(msg, code);
            }
        }
        return new RelayTickResult(relayed, dedupSuppressed, governanceRejected, skipped);
    }

    /**
     * Build the hop2 forward envelope from the inbound message + decoded descriptor.
     *
     * <p>The hop2 {@code messageId} mirrors the hop1 broker id so the outbox dedup
     * {@code (tenantId, messageId)} also guards against a double-relay of a replayed
     * hop1 (defence in depth behind the inbox dedup). {@code correlationId} /
     * {@code routeHandle} / {@code capability} / {@code deadline} pass through from
     * the descriptor; {@code sourceServiceId} = this relay; {@code targetServiceId} =
     * the original runtime target; {@code payloadRef} = the same descriptor (so the
     * runtime can decode it on hop2).
     */
    private ForwardingEnvelope buildHop2Envelope(BrokerInboundMessage msg,
                                                 BrokerControlDescriptor.Descriptor desc) {
        return new ForwardingEnvelope(
                new ForwardingMessageId(msg.messageId()),
                desc.eventType(),
                msg.tenantId(),
                desc.traceId(),
                desc.correlationId(),
                desc.idempotencyKey(),
                new ForwardingRouteHandle(desc.routeHandle(), msg.tenantId()),
                desc.capability(),
                sourceServiceId,
                msg.targetServiceId(),
                desc.deadlineMillisEpoch(),
                ForwardingEnvelope.PayloadPolicy.DATA_BEARING,
                msg.payloadRef());
    }

    /**
     * Commit a poison message (do NOT redeliver — a broker redelivery cannot fix a
     * malformed / data-integrity failure and would loop) and mark the inbox REJECTED
     * for audit.
     */
    private void rejectPoison(BrokerInboundMessage msg, ForwardingFailureCode code) {
        consumer.commit(msg);
        inbox.markRejected(new ForwardingMessageId(msg.messageId()), msg.tenantId(),
                consumerServiceId, code);
    }

    /** Immutable summary of one relay tick. */
    public record RelayTickResult(int relayed, int dedupSuppressed, int governanceRejected, int skipped) {
        public RelayTickResult {
            if (relayed < 0 || dedupSuppressed < 0 || governanceRejected < 0 || skipped < 0) {
                throw new IllegalArgumentException("tick counts must be non-negative");
            }
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
