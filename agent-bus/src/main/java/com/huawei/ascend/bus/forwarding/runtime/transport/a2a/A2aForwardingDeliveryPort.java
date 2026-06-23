package com.huawei.ascend.bus.forwarding.runtime.transport.a2a;

import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A2A JSON-RPC {@link ForwardingDeliveryPort} — Stage 15 PoC real transport binding.
 *
 * <p>This is the concrete delivery that proves agent-bus's C3 forwarding can
 * consume agent-runtime: {@code deliver} constructs a valid A2A JSON-RPC
 * {@code message/stream} request, drives it against a remote {@code /a2a}
 * endpoint (agent-runtime's {@code A2aJsonRpcController}), and maps the remote
 * Task lifecycle back onto the outbox state machine's
 * {@link ForwardingDeliveryResult}. Modeled directly on main's
 * {@code A2aRemoteAgentOutboundAdapter} (per-endpoint transport cache + blocking
 * streaming await + terminal / timeout / error classification), but emits a
 * single {@link ForwardingDeliveryResult} instead of a result list — it is a
 * delivery port, not an invocation service.
 *
 * <h3>Semantics — synchronous wait for completion</h3>
 *
 * <p>Per the Stage 15 scope decision, {@code deliver} blocks on the A2A stream
 * until the remote Task reaches a terminal / interrupted state (= Stage 13's T1
 * dispatcher-push over sync RPC). The outbox is ACKed only when the remote Task
 * is {@code COMPLETED} (success) or {@code INPUT_REQUIRED} (delivery reached the
 * remote, which now needs interaction — a PoC tradeoff; precise half-done
 * semantics deferred). A non-COMPLETED final terminal (FAILED / CANCELED / REJECTED)
 * is a remote agent's terminal business failure → {@code dlq(REMOTE_TASK_FAILED)}
 * (Stage 18 MI18-002): retrying the same input to a deterministically failing task
 * would only bomb the downstream. {@code AUTH_REQUIRED} (interrupted) stays
 * {@code retry(RECEIVER_UNAVAILABLE)} — authentication is recoverable, not a task failure.
 *
 * <h3>HD4 preserved</h3>
 *
 * <p>{@code deliver} never unwraps {@link com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle}.
 * The opaque handle is resolved to an endpoint URL by the injected
 * {@link ForwardingEndpointResolver}; an empty resolution maps to
 * {@code dlq(ROUTE_NOT_FOUND)}.
 *
 * <h3>§6.2 preserved</h3>
 *
 * <p>The A2A {@code Message} text part carries only a short routing descriptor
 * (the forwarding {@code messageId}) — never a payload body or token stream. The
 * {@code payloadRef} data reference travels in {@code MessageSendParams.metadata}
 * (A2A's extension slot), the same way agent-runtime's adapter carries its
 * {@code arguments}. No concrete broker / MQ client is involved (A2A is HTTP
 * JSON-RPC, not Kafka / RabbitMQ / NATS).
 *
 * <h3>Contract</h3>
 *
 * <p>Satisfies the {@link ForwardingDeliveryPort} contract: every transport
 * outcome (success, timeout, remote failure, network error, route resolution
 * failure) maps to a {@link ForwardingDeliveryResult}; {@code deliver} never
 * throws a non-lease {@link RuntimeException}. The A2A SDK's
 * {@link A2AClientException} (an unchecked exception, thrown synchronously when
 * the stream cannot open) and any runtime error are caught and classified
 * (Stage 11 MI11-002 contract).
 *
 * <p>ArchUnit: the {@code org.a2aproject} surface is confined to this
 * {@code transport.a2a} subpackage
 * ({@code AgentBusForwardingSpiPurityTest#forwarding_core_does_not_import_a2a_outside_transport_adapter});
 * forwarding ports / state machine / worker / loop stay transport-agnostic.
 *
 * <p>Authority: Stage 15 PoC — A2A transport adapter
 * ({@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage14-review-and-stage15-plan.md}).
 */
// scope: forwarding runtime transport adapter — A2A SDK confined to transport.a2a
public final class A2aForwardingDeliveryPort implements ForwardingDeliveryPort {

    private final ForwardingEndpointResolver endpointResolver;
    private final A2aForwardingProperties properties;
    // One JSONRPCTransport per endpoint URL, reused across deliveries (mirrors
    // A2aRemoteAgentOutboundAdapter's per-endpoint cache). PoC does not handle
    // endpoint churn / close-on-rebuild — the resolver returns stable endpoints
    // for the PoC; production lifecycle (registry integration, connection pool
    // governance) is deferred.
    private final Map<String, ClientTransport> transportByEndpoint = new ConcurrentHashMap<>();

    public A2aForwardingDeliveryPort(ForwardingEndpointResolver endpointResolver) {
        this(endpointResolver, A2aForwardingProperties.DEFAULT);
    }

    public A2aForwardingDeliveryPort(ForwardingEndpointResolver endpointResolver,
                                     A2aForwardingProperties properties) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public ForwardingDeliveryResult deliver(ForwardingOutboxRecord record, long nowMillisEpoch) {
        // HD4: resolve the opaque routeHandle via the injected port — never unwrap it here.
        Optional<String> endpoint = endpointResolver.resolve(record.routeHandle());
        if (endpoint.isEmpty()) {
            return ForwardingDeliveryResult.dlq(ForwardingFailureCode.ROUTE_NOT_FOUND);
        }
        ClientTransport transport = transportByEndpoint.computeIfAbsent(endpoint.get(), JSONRPCTransport::new);

        MessageSendParams params = toMessageSendParams(record);
        // R-C.c: tenant continuity travels as an HTTP header matching the
        // receiving A2aJsonRpcController @RequestHeader name, taking precedence
        // over any self-declared tenant in the params.
        ClientCallContext context = new ClientCallContext(
                Map.of(),
                Map.of(properties.tenantHeaderName(), record.tenantId()));

        CountDownLatch settled = new CountDownLatch(1);
        AtomicReference<TaskState> terminalState = new AtomicReference<>();

        try {
            transport.sendMessageStreaming(
                    params,
                    event -> {
                        TaskState state = terminalStateOf(event);
                        if (state != null) {
                            terminalState.set(state);
                            settled.countDown();
                        }
                    },
                    // A mid-stream error with no preceding terminal Task event is
                    // classified below as RECEIVER_UNAVAILABLE; the throwable itself
                    // is not mapped onto a distinct failure code in the PoC.
                    error -> settled.countDown(),
                    context);
        } catch (RuntimeException ex) {
            // Synchronous throw from sendMessageStreaming — A2AClientException is an
            // unchecked subclass of RuntimeException — or any other runtime failure
            // before / at stream open. Receiver unreachable; retryable.
            return ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
        }

        boolean reachedTerminal;
        try {
            reachedTerminal = settled.await(properties.streamTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ForwardingDeliveryResult.retry(ForwardingFailureCode.DELIVERY_TIMEOUT);
        }

        if (!reachedTerminal) {
            // Stream signalled neither a terminal event nor an error within the timeout.
            return ForwardingDeliveryResult.retry(ForwardingFailureCode.DELIVERY_TIMEOUT);
        }

        TaskState state = terminalState.get();
        if (state != null) {
            // COMPLETED / INPUT_REQUIRED: delivery reached a success / interaction-needed state.
            if (state == TaskState.TASK_STATE_COMPLETED || state == TaskState.TASK_STATE_INPUT_REQUIRED) {
                return ForwardingDeliveryResult.acked();
            }
            if (state.isFinal()) {
                // isFinal && !COMPLETED (FAILED / CANCELED / REJECTED): a remote agent's
                // terminal business failure — non-retryable REMOTE_TASK_FAILED → direct DLQ.
                // Retrying the same input to a deterministically failing task would only
                // bomb the downstream (Stage 18 MI18-002). isFinal() — not enumerated case
                // labels — so any future A2A final state stays correctly classified.
                return ForwardingDeliveryResult.dlq(ForwardingFailureCode.REMOTE_TASK_FAILED);
            }
            // interrupted && !INPUT_REQUIRED → AUTH_REQUIRED: delivery reached the remote but
            // it needs authentication — recoverable, retry as infra-layer RECEIVER_UNAVAILABLE.
            return ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
        }
        // The error consumer fired without a terminal Task event — the stream
        // failed mid-flight (network drop / decode error). Retryable.
        return ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
    }

    /**
     * Extracts the terminal / interrupted {@link TaskState} from a stream event,
     * or {@code null} if the event is non-terminal (working / artifact / plain
     * message). Terminal = {@code isFinal()} (COMPLETED / FAILED / CANCELED /
     * REJECTED) or {@code isInterrupted()} (INPUT_REQUIRED / AUTH_REQUIRED),
     * mirroring {@code A2aJsonRpcController#isStreamTerminating}.
     */
    private static TaskState terminalStateOf(StreamingEventKind event) {
        TaskStatus status;
        if (event instanceof Task task) {
            status = task.status();
        } else if (event instanceof TaskStatusUpdateEvent update) {
            status = update.status();
        } else {
            return null;
        }
        if (status == null || status.state() == null) {
            return null;
        }
        TaskState state = status.state();
        return (state.isFinal() || state.isInterrupted()) ? state : null;
    }

    private static MessageSendParams toMessageSendParams(ForwardingOutboxRecord record) {
        String messageId = record.messageId().value();
        // TextPart carries only a routing descriptor — never the payload body (§6.2).
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(messageId)
                .parts(List.<Part<?>>of(new TextPart("agent-bus forwarded message " + messageId)))
                .build();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", record.tenantId());
        metadata.put("sourceServiceId", record.sourceServiceId());
        metadata.put("targetServiceId", record.targetServiceId());
        // payloadRef is the data-reference path (§6.2); CONTROL_ONLY messages omit it.
        if (record.payloadRef() != null) {
            metadata.put("payloadRef", record.payloadRef());
        }
        return MessageSendParams.builder()
                .message(message)
                .metadata(Map.copyOf(metadata))
                .build();
    }
}
