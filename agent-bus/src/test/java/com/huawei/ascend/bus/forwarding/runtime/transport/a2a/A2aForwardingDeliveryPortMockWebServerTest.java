package com.huawei.ascend.bus.forwarding.runtime.transport.a2a;

import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.MapEndpointResolver;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 15 PoC contract harness — proves {@link A2aForwardingDeliveryPort}
 * drives a real A2A JSON-RPC {@code /a2a} endpoint (simulated by MockWebServer)
 * and maps the remote Task lifecycle back onto {@link ForwardingDeliveryResult}.
 *
 * <p><b>Wire-format symmetry.</b> The SSE response MockWebServer serves is built
 * with the A2A SDK's OWN serializer —
 * {@code JsonUtil.toJson(new SendStreamingMessageResponse(id, event))} — exactly
 * what agent-runtime's {@code A2aJsonRpcController} emits per frame
 * ({@code ServerSentEvent.event("jsonrpc").data(...)}). So the bytes the real
 * {@code JSONRPCTransport} client parses are byte-identical to a real
 * agent-runtime response: this is real protocol code against a real (in-process)
 * server, not a {@code RecordingTransport} fake at the {@code ClientTransport}
 * seam (which is how agent-runtime's own adapter is tested). Same "verify real
 * protocol code against an in-process server" philosophy as Stage 12's
 * embedded-postgres.
 *
 * <p><b>Five scenarios</b> covering the {@link ForwardingDeliveryPort} contract:
 * <ol>
 *   <li>COMPLETED → ACKED (happy path: remote task reached terminal-success).
 *   <li>no terminal event within the stream timeout → RETRY/DELIVERY_TIMEOUT.
 *   <li>remote Task FAILED → DLQ/REMOTE_TASK_FAILED (Stage 18: a remote agent's
 *       terminal business failure is non-retryable; routes straight to DLQ, not
 *       retried as a transient infra failure).
 *   <li>connection-level failure (dropped socket) → RETRY/RECEIVER_UNAVAILABLE.
 *   <li>endpoint resolver returns empty → DLQ/ROUTE_NOT_FOUND (HD4 + no network).
 * </ol>
 *
 * <p><b>Stage 23 (MI23-003) &mdash; payloadRef transport-boundary symmetry.</b> Two
 * additional scenarios close the Stage 15 blind spot where {@code record()} set
 * {@code payloadRef="payload-ref-1"} but no test asserted it reached the wire:
 * <ol start="6">
 *   <li>DATA_BEARING &rarr; the payloadRef travels into the A2A request metadata
 *       ({@code metadata.payloadRef} injected when {@code record.payloadRef() != null}).</li>
 *   <li>CONTROL_ONLY &rarr; the payloadRef is OMITTED from the A2A request (the
 *       symmetric branch; the &sect;6.2 data-reference-path contract at the
 *       transport boundary).</li>
 * </ol>
 *
 * <p>Authority: Stage 15 PoC — A2A transport adapter
 * ({@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage14-review-and-stage15-plan.md}).
 */
class A2aForwardingDeliveryPortMockWebServerTest {

    private static final String TENANT = "tenant-a";
    private static final String ROUTE = "route-1";
    private static final String SOURCE = "svc-source";
    private static final String TARGET = "svc-target";

    private MockWebServer server;
    private ForwardingEndpointResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        resolver = new MapEndpointResolver(Map.of(ROUTE, server.url("/a2a").toString()));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    /** A fresh PENDING outbox record (no lease, no failure code) — deliver reads
     *  only the envelope fields, never the status. */
    private static ForwardingOutboxRecord record() {
        return new ForwardingOutboxRecord(
                TENANT,
                new ForwardingMessageId("msg-1"),
                SOURCE,
                TARGET,
                new ForwardingRouteHandle(ROUTE, TENANT),
                "payload-ref-1",
                ForwardingStatus.Outbox.PENDING,
                0, 0L, 1L, 1L,
                null, null);
    }

    /** A CONTROL_ONLY outbox record (payloadRef=null) — the pure-control variant.
     *  Stage 23 MI23-003: the symmetric counterpart to {@link #record()}, used to
     *  prove that {@code toMessageSendParams} OMITS the payloadRef from the A2A
     *  request metadata when the record carries no payload reference (the
     *  CONTROL_ONLY branch of the &sect;6.2 data-reference contract). */
    private static ForwardingOutboxRecord controlOnlyRecord() {
        return new ForwardingOutboxRecord(
                TENANT,
                new ForwardingMessageId("msg-control"),
                SOURCE,
                TARGET,
                new ForwardingRouteHandle(ROUTE, TENANT),
                null,
                ForwardingStatus.Outbox.PENDING,
                0, 0L, 1L, 1L,
                null, null);
    }

    /** A single SSE {@code jsonrpc} frame carrying {@code event} as the stream
     *  result — byte-identical to what agent-runtime's controller emits. */
    private static String sseFrame(StreamingEventKind event) throws JsonProcessingException {
        String data = JsonUtil.toJson(new SendStreamingMessageResponse("1", event));
        return "event:jsonrpc\ndata:" + data + "\n\n";
    }

    private static Task task(TaskState state) {
        return Task.builder()
                .id("remote-task-1")
                .contextId("remote-ctx-1")
                .status(new TaskStatus(state, null, null))
                .build();
    }

    private A2aForwardingDeliveryPort port(long streamTimeoutMillis) {
        return new A2aForwardingDeliveryPort(resolver,
                new A2aForwardingProperties(streamTimeoutMillis, "X-Tenant-Id"));
    }

    @Test
    void deliver_returns_acked_when_remote_task_completes() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseFrame(task(TaskState.TASK_STATE_COMPLETED))));

        ForwardingDeliveryResult result = port(5_000L).deliver(record(), 0L);

        assertThat(result.outcome()).isEqualTo(ForwardingDeliveryResult.Outcome.ACKED);
        // R-C.c: the tenant continuity travels as the X-Tenant-Id header, matching
        // agent-runtime's A2aJsonRpcController @RequestHeader name.
        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("X-Tenant-Id")).isEqualTo(TENANT);
        // The real JSONRPCTransport issued a SendStreamingMessage JSON-RPC call.
        assertThat(request.getBody().readUtf8()).contains("SendStreamingMessage");
    }

    @Test
    void deliver_retries_on_stream_timeout_when_no_terminal_event() throws Exception {
        // Server holds the SSE body back past the stream timeout — no terminal
        // event arrives in time. deliver classifies DELIVERY_TIMEOUT (retryable).
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseFrame(task(TaskState.TASK_STATE_COMPLETED)))
                .setBodyDelay(2, TimeUnit.SECONDS));

        ForwardingDeliveryResult result = port(200L).deliver(record(), 0L);

        assertThat(result.outcome()).isEqualTo(ForwardingDeliveryResult.Outcome.RETRY_SCHEDULED);
        assertThat(result.failureCode()).isEqualTo(ForwardingFailureCode.DELIVERY_TIMEOUT);
    }

    @Test
    void deliver_dlqs_on_remote_task_failed() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseFrame(task(TaskState.TASK_STATE_FAILED))));

        ForwardingDeliveryResult result = port(5_000L).deliver(record(), 0L);

        // Stage 18 MI18-002: a remote agent's terminal business failure (A2A FAILED) is
        // non-retryable — routes straight to DLQ/REMOTE_TASK_FAILED, not retried as a
        // transient infra failure.
        assertThat(result.outcome()).isEqualTo(ForwardingDeliveryResult.Outcome.DLQ);
        assertThat(result.failureCode()).isEqualTo(ForwardingFailureCode.REMOTE_TASK_FAILED);
    }

    @Test
    void deliver_retries_on_connection_error() throws Exception {
        // The server reads the request then drops the connection with no
        // response — a genuine transport-level failure. (A bare HTTP 4xx/5xx
        // status does NOT exercise this path: the SDK treats a non-2xx as a
        // silent empty SSE stream and deliver blocks to DELIVERY_TIMEOUT.
        // A dropped socket surfaces a connection failure instead.)
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

        ForwardingDeliveryResult result = port(5_000L).deliver(record(), 0L);

        assertThat(result.outcome()).isEqualTo(ForwardingDeliveryResult.Outcome.RETRY_SCHEDULED);
        assertThat(result.failureCode()).isEqualTo(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
    }

    @Test
    void deliver_dlqs_when_endpoint_resolver_returns_empty() {
        // HD4: the opaque routeHandle cannot be resolved — no network call is
        // attempted, the message goes straight to DLQ/ROUTE_NOT_FOUND.
        ForwardingEndpointResolver emptyResolver = new MapEndpointResolver(Map.of());
        ForwardingDeliveryResult result =
                new A2aForwardingDeliveryPort(emptyResolver).deliver(record(), 0L);

        assertThat(result.outcome()).isEqualTo(ForwardingDeliveryResult.Outcome.DLQ);
        assertThat(result.failureCode()).isEqualTo(ForwardingFailureCode.ROUTE_NOT_FOUND);
    }

    @Test
    void deliver_carries_payload_ref_in_a2a_metadata_for_data_bearing_record() throws Exception {
        // Stage 23 MI23-003: a DATA_BEARING record's payloadRef travels into the A2A
        // request metadata (A2aForwardingDeliveryPort.toMessageSendParams injects
        // metadata.payloadRef when record.payloadRef() != null). The existing
        // record() helper already sets payloadRef="payload-ref-1", but no prior
        // Stage asserted it reached the wire — this closes the Stage 15 transport
        // blind spot (the third high-value blind spot, per the Stage 23 plan §2.4).
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseFrame(task(TaskState.TASK_STATE_COMPLETED))));

        ForwardingDeliveryResult result = port(5_000L).deliver(record(), 0L);

        assertThat(result.outcome()).isEqualTo(ForwardingDeliveryResult.Outcome.ACKED);
        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body)
                .as("DATA_BEARING: the payloadRef field name travels into the A2A request metadata")
                .contains("payloadRef");
        assertThat(body)
                .as("the payloadRef reference value is on the wire "
                    + "(a String reference, not a payload body — §6.2)")
                .contains("payload-ref-1");
    }

    @Test
    void deliver_omits_payload_ref_for_control_only_record() throws Exception {
        // Stage 23 MI23-003: a CONTROL_ONLY record (payloadRef=null) must NOT carry
        // a payloadRef in the A2A request — toMessageSendParams only injects the
        // metadata entry when payloadRef != null. The two-branch symmetry
        // (DATA_BEARING carries, CONTROL_ONLY omits) IS the §6.2 data-reference-path
        // contract at the transport boundary.
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseFrame(task(TaskState.TASK_STATE_COMPLETED))));

        ForwardingDeliveryResult result = port(5_000L).deliver(controlOnlyRecord(), 0L);

        assertThat(result.outcome()).isEqualTo(ForwardingDeliveryResult.Outcome.ACKED);
        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body)
                .as("CONTROL_ONLY: no payloadRef in the A2A request "
                    + "(metadata omitted when payloadRef is null)")
                .doesNotContain("payloadRef");
    }
}
