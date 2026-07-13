package com.huawei.ascend.bus.test;

import com.huawei.ascend.bus.forwarding.runtime.transport.broker.InMemoryBroker;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.test.InMemoryForwardingOutbox;

import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for {@link TestAgentRuntime} — the in-repo test double that
 * stands in for the external {@code agent-runtime-java} as the agent-runtime END
 * of the FEAT-013/014 forwarding chain.
 *
 * <p>Pins the L2 §6 scenarios (FEAT-013 §4.3 response state machine + §4.4
 * idempotency): blocking final response, degenerate to Task ref, UNKNOWN +
 * same-key retry, streaming, idempotent duplicate, the FEAT-001 stubs
 * (Cancel/Query/Subscribe), and the FEAT-014 A2A family. Each scenario publishes
 * a request onto an {@link InMemoryBroker}, ticks {@link TestAgentRuntime#pollAndProcess},
 * and asserts the produced response sequence + taskId semantics + broker delivery.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §4.3 / §4.4 / §6};
 * {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-014-a2a-call-event-forwarding.md §4.4}.
 */
class TestAgentRuntimeTest {

    private static final String TENANT = "tenant-a";
    private static final String RUNTIME = "test-agent-runtime";
    private static final String GATEWAY = "test-gateway";
    private static final String ROUTE = "route-tenant-a";

    // ===== §6.2.1 blocking call returns final response =====

    @Test
    void blocking_call_returns_final_response_sequence() {
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        ForwardingEnvelope req = request("req-1", AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "idem-1");
        TestAgentRuntime.publishRequest(runtime.broker(), req, 1_000L);

        TestAgentRuntime.ProcessingOutcome out = runtime.pollAndProcess(2_000L);

        assertThat(out.outcome()).isEqualTo(TestAgentRuntime.ProcessingOutcome.Outcome.PROCESSED);
        assertThat(out.requestMessageId()).isEqualTo("req-1");
        assertThat(out.responseEventTypes()).containsExactly(
                AgentBusEventType.INVOCATION_ACCEPTED,
                AgentBusEventType.INVOCATION_RESPONSE,
                AgentBusEventType.INVOCATION_TERMINAL);
        // each response carried the same taskId, swapped source/target, same correlation/trace
        String taskId = extractTaskId(out.responses().get(0));
        assertThat(out.responses()).allSatisfy(r -> {
            assertThat(r.sourceServiceId()).isEqualTo(RUNTIME);
            assertThat(r.targetServiceId()).isEqualTo(GATEWAY);
            assertThat(r.tenantId()).isEqualTo(TENANT);
            assertThat(r.traceId()).isEqualTo("trace-1");
            assertThat(r.correlationId()).isEqualTo("corr-1");
            assertThat(r.idempotencyKey()).isEqualTo("idem-1");
        });
        assertThat(extractTaskId(out.responses().get(0))).isEqualTo(taskId);
        assertThat(extractStatus(out.responses().get(2))).isEqualTo("completed");
        // all three responses reached the broker (per-message introspection)
        for (ForwardingEnvelope r : out.responses()) {
            assertThat(runtime.broker().outboundMessage(TENANT, r.messageId().value())).isNotNull();
        }
        // the request was committed (model B ack-after-consume) — re-poll yields only responses, then idle
        assertThat(runtime.pollAndProcess(3_000L).outcome())
                .isEqualTo(TestAgentRuntime.ProcessingOutcome.Outcome.IDLE);
    }

    // ===== §6.2.2 degenerate to Task ref (ACCEPTED only) =====

    @Test
    void degenerate_to_task_ref_emits_accepted_only() {
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        runtime.setResponseMode(TestAgentRuntime.ResponseMode.ACCEPTED_ONLY);
        ForwardingEnvelope req = request("req-2", AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "idem-2");
        TestAgentRuntime.publishRequest(runtime.broker(), req, 1_000L);

        TestAgentRuntime.ProcessingOutcome out = runtime.pollAndProcess(2_000L);

        // ACCEPTED only — the gateway would observe ACCEPTED_WITH_TASK (no final response in window)
        assertThat(out.responseEventTypes()).containsExactly(AgentBusEventType.INVOCATION_ACCEPTED);
        assertThat(extractTaskId(out.responses().get(0))).startsWith("task-");
        assertThat(extractStatus(out.responses().get(0))).isNull();
    }

    // ===== §6.2.3 UNKNOWN + same-key retry → same taskId =====

    @Test
    void unknown_then_same_key_retry_returns_same_task_id() {
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        // first pass: SILENT — runtime creates the task but emits nothing → gateway times out → UNKNOWN
        runtime.setResponseMode(TestAgentRuntime.ResponseMode.SILENT);
        ForwardingEnvelope req = request("req-3", AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "idem-3");
        TestAgentRuntime.publishRequest(runtime.broker(), req, 1_000L);

        TestAgentRuntime.ProcessingOutcome silent = runtime.pollAndProcess(2_000L);
        assertThat(silent.responses()).isEmpty();
        // the task WAS created internally even though nothing was emitted
        Optional<String> silentTaskId = runtime.taskIdFor("idem-3");
        assertThat(silentTaskId).isPresent();

        // retry: same idempotencyKey, now BLOCKING → same taskId, full sequence
        runtime.setResponseMode(TestAgentRuntime.ResponseMode.BLOCKING);
        ForwardingEnvelope retry = request("req-3b", AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "idem-3");
        TestAgentRuntime.publishRequest(runtime.broker(), retry, 3_000L);

        TestAgentRuntime.ProcessingOutcome retryOut = runtime.pollAndProcess(4_000L);
        assertThat(retryOut.responseEventTypes()).containsExactly(
                AgentBusEventType.INVOCATION_ACCEPTED,
                AgentBusEventType.INVOCATION_RESPONSE,
                AgentBusEventType.INVOCATION_TERMINAL);
        assertThat(extractTaskId(retryOut.responses().get(0))).isEqualTo(silentTaskId.orElseThrow());
    }

    // ===== §6.2.4 streaming call =====

    @Test
    void streaming_call_emits_accepted_then_stream_ready() {
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        runtime.setResponseMode(TestAgentRuntime.ResponseMode.STREAMING);
        ForwardingEnvelope req = request("req-4", AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "idem-4");
        TestAgentRuntime.publishRequest(runtime.broker(), req, 1_000L);

        TestAgentRuntime.ProcessingOutcome out = runtime.pollAndProcess(2_000L);

        assertThat(out.responseEventTypes()).containsExactly(
                AgentBusEventType.INVOCATION_ACCEPTED,
                AgentBusEventType.INVOCATION_STREAM_READY);
        String taskId = extractTaskId(out.responses().get(0));
        // STREAM_READY carries a streamRef keyed on the taskId
        assertThat(out.responses().get(1).payloadRef()).contains("streamRef=stream://" + taskId);
    }

    // ===== §4.4 idempotent duplicate → same taskId, no second logical call =====

    @Test
    void idempotent_duplicate_returns_same_task_id_no_second_logical_call() {
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        ForwardingEnvelope req = request("req-5", AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "idem-5");
        TestAgentRuntime.publishRequest(runtime.broker(), req, 1_000L);
        TestAgentRuntime.ProcessingOutcome first = runtime.pollAndProcess(2_000L);
        String firstTaskId = extractTaskId(first.responses().get(0));
        assertThat(first.responseEventTypes()).containsExactly(
                AgentBusEventType.INVOCATION_ACCEPTED,
                AgentBusEventType.INVOCATION_RESPONSE,
                AgentBusEventType.INVOCATION_TERMINAL);

        // duplicate: same tenantId + idempotencyKey → same taskId, no second logical call (no RESPONSE/TERMINAL)
        ForwardingEnvelope dup = request("req-5b", AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "idem-5");
        TestAgentRuntime.publishRequest(runtime.broker(), dup, 3_000L);
        TestAgentRuntime.ProcessingOutcome second = runtime.pollAndProcess(4_000L);

        assertThat(second.responseEventTypes()).containsExactly(AgentBusEventType.INVOCATION_ACCEPTED);
        assertThat(extractTaskId(second.responses().get(0))).isEqualTo(firstTaskId);
    }

    // ===== FEAT-001 stubs: Cancel / Query / Subscribe =====

    @Test
    void cancel_request_emits_cancelled_terminal() {
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        ForwardingEnvelope req = request("req-6", AgentBusEventType.CLIENT_INVOCATION_CANCEL_REQUESTED, "idem-6");
        TestAgentRuntime.publishRequest(runtime.broker(), req, 1_000L);

        TestAgentRuntime.ProcessingOutcome out = runtime.pollAndProcess(2_000L);

        assertThat(out.responseEventTypes()).containsExactly(AgentBusEventType.INVOCATION_TERMINAL);
        assertThat(extractStatus(out.responses().get(0))).isEqualTo("cancelled");
    }

    @Test
    void query_request_emits_task_snapshot_response() {
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        ForwardingEnvelope req = request("req-7", AgentBusEventType.CLIENT_INVOCATION_QUERY_REQUESTED, "idem-7");
        TestAgentRuntime.publishRequest(runtime.broker(), req, 1_000L);

        TestAgentRuntime.ProcessingOutcome out = runtime.pollAndProcess(2_000L);

        assertThat(out.responseEventTypes()).containsExactly(AgentBusEventType.INVOCATION_RESPONSE);
        assertThat(extractStatus(out.responses().get(0))).isEqualTo("snapshot");
    }

    @Test
    void stream_subscribe_emits_stream_ready() {
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        ForwardingEnvelope req = request("req-8", AgentBusEventType.CLIENT_STREAM_SUBSCRIBE_REQUESTED, "idem-8");
        TestAgentRuntime.publishRequest(runtime.broker(), req, 1_000L);

        TestAgentRuntime.ProcessingOutcome out = runtime.pollAndProcess(2_000L);

        assertThat(out.responseEventTypes()).containsExactly(AgentBusEventType.INVOCATION_STREAM_READY);
        assertThat(out.responses().get(0).payloadRef()).startsWith("streamRef=stream://");
    }

    // ===== FEAT-014 A2A family =====

    @Test
    void a2a_call_requested_emits_accepted_response_terminal() {
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        ForwardingEnvelope req = request("req-9", AgentBusEventType.A2A_CALL_REQUESTED, "idem-9");
        TestAgentRuntime.publishRequest(runtime.broker(), req, 1_000L);

        TestAgentRuntime.ProcessingOutcome out = runtime.pollAndProcess(2_000L);

        assertThat(out.responseEventTypes()).containsExactly(
                AgentBusEventType.A2A_CALL_ACCEPTED,
                AgentBusEventType.A2A_CALL_RESPONSE,
                AgentBusEventType.A2A_CALL_TERMINAL);
        String taskId = extractTaskId(out.responses().get(0));
        assertThat(extractStatus(out.responses().get(2))).isEqualTo("completed");
        // a2a responses swap source/target the same way (source=runtime, target=caller)
        assertThat(out.responses().get(0).sourceServiceId()).isEqualTo(RUNTIME);
        assertThat(out.responses().get(0).targetServiceId()).isEqualTo(GATEWAY);
        // a2a idempotency: same key → same taskId
        assertThat(runtime.taskIdFor("idem-9")).hasValue(taskId);
    }

    // ===== encoding / robustness =====

    @Test
    void descriptor_round_trip_preserves_all_control_fields() {
        // buildRequest encoded the control fields into payloadRef; the runtime decoded them and built
        // responses carrying the SAME correlationId/traceId/idempotencyKey/routeHandle/capability/deadline.
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        ForwardingEnvelope req = request("req-rt", AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "idem-rt");
        TestAgentRuntime.publishRequest(runtime.broker(), req, 1_000L);
        TestAgentRuntime.ProcessingOutcome out = runtime.pollAndProcess(2_000L);
        ForwardingEnvelope accepted = out.responses().get(0);
        assertThat(accepted.correlationId()).isEqualTo("corr-rt");
        assertThat(accepted.traceId()).isEqualTo("trace-rt");
        assertThat(accepted.idempotencyKey()).isEqualTo("idem-rt");
        assertThat(accepted.routeHandle().value()).isEqualTo(ROUTE);
        assertThat(accepted.routeHandle().tenantScope()).isEqualTo(TENANT);
        assertThat(accepted.capability()).isEqualTo("cap-1");
        assertThat(accepted.deadlineMillisEpoch()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void idle_when_no_request_pending() {
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        TestAgentRuntime.ProcessingOutcome out = runtime.pollAndProcess(1_000L);
        assertThat(out.outcome()).isEqualTo(TestAgentRuntime.ProcessingOutcome.Outcome.IDLE);
        assertThat(out.responses()).isEmpty();
    }

    @Test
    void self_produced_responses_are_skipped_not_reprocessed() {
        // after processing a request, the responses sit on the same topic; the runtime must NOT
        // reprocess them as requests — it commits (skips) them and goes idle.
        TestAgentRuntime runtime = newRuntime(r -> Optional.of("topic-" + r.tenantScope()));
        TestAgentRuntime.publishRequest(runtime.broker(),
                request("req-10", AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "idem-10"), 1_000L);
        runtime.pollAndProcess(2_000L); // produces 3 responses
        // drain the 3 self-produced responses (each is a non-request → skip+commit)
        for (int i = 0; i < 3; i++) {
            assertThat(runtime.pollAndProcess(3_000L + i).outcome())
                    .isEqualTo(TestAgentRuntime.ProcessingOutcome.Outcome.IDLE);
        }
        assertThat(runtime.pollAndProcess(10_000L).outcome())
                .isEqualTo(TestAgentRuntime.ProcessingOutcome.Outcome.IDLE);
    }

    // ===== fixtures =====

    private TestAgentRuntime newRuntime(Function<com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle,
            Optional<String>> resolver) {
        InMemoryBroker broker = new InMemoryBroker(resolver::apply);
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        return new TestAgentRuntime(broker, outbox, RUNTIME, TENANT);
    }

    private static ForwardingEnvelope request(String messageId, AgentBusEventType eventType, String idempotencyKey) {
        return TestAgentRuntime.buildRequest(messageId, eventType, TENANT,
                "trace-" + idempotencyKey.replace("idem-", ""), "corr-" + idempotencyKey.replace("idem-", ""),
                idempotencyKey, ROUTE, "cap-1", GATEWAY, RUNTIME, Long.MAX_VALUE);
    }

    /** Extract {@code taskId=<id>} from a response payloadRef, or null if absent. */
    private static String extractTaskId(ForwardingEnvelope env) {
        return token(env.payloadRef(), "taskId");
    }

    /** Extract {@code status=<status>} from a response payloadRef, or null if absent. */
    private static String extractStatus(ForwardingEnvelope env) {
        return token(env.payloadRef(), "status");
    }

    private static String token(String descriptor, String key) {
        for (String pair : descriptor.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
