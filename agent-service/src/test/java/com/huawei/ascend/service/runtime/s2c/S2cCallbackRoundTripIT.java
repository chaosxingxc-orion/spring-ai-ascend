package com.huawei.ascend.service.runtime.s2c;

import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.engine.exec.IterativeAgentLoopExecutor;
import com.huawei.ascend.engine.exec.SequentialGraphExecutor;
import com.huawei.ascend.service.runtime.orchestration.TestEnginePorts;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.SuspendSignal;
import com.huawei.ascend.service.runtime.resilience.spi.SuspendReason;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end Server-to-Client capability callback flow.
 *
 * <p>An agent-loop NodeFunction throws {@code SuspendSignal.forClientCallback(...)}
 * at iteration 0 (refactored in v2.0.0-rc3 per cross-constraint audit α-2 / β-5
 * from the prior unchecked {@code S2cCallbackSignal} to the checked
 * {@code SuspendSignal} with {@code isClientCallback()=true}). The
 * SyncOrchestrator catches it, dispatches via the registered
 * InMemoryS2cCallbackTransport, validates the response, and resumes the loop
 * with the validated payload.
 *
 * <p>Asserts:
 * <ul>
 *   <li>Happy path: outcome=OK -> Run.SUCCEEDED, payload propagates.</li>
 *   <li>callback_id mismatch -> Run transitions through ON_ERROR with s2c_response_invalid.</li>
 *   <li>outcome=ERROR -> Run transitions through ON_ERROR with s2c_client_error.</li>
 *   <li>No transport registered -> Run fails with s2c_transport_unavailable.</li>
 * </ul>
 *
 * <p>Authority: ADR-0074; CLAUDE.md Rule R-M.d.
 * Enforcer row: docs/governance/enforcers.yaml#E82 (S2C round-trip integration).
 */
class S2cCallbackRoundTripIT {

    private static final String VALID_TRACE = "abcdef1234567890abcdef1234567890";
    private static final String CLIENT_TRACE = "1234567890abcdef1234567890abcdef";

    private static S2cCallbackEnvelope envelopeFor(UUID serverRunId) {
        return new S2cCallbackEnvelope(
                UUID.randomUUID(),
                serverRunId,
                "client.test.capability",
                "request-payload",
                VALID_TRACE,
                UUID.randomUUID(),
                null,
                Map.of());
    }

    private static ExecutorDefinition.AgentLoopDefinition agentThatCallsClient(
            AtomicReference<S2cCallbackEnvelope> capturedEnvelope) {
        return new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iter) -> {
                    // Post-review fix (plan B / P0-1): IterativeAgentLoopExecutor
                    // passes AgentLoopDefinition.initialContext (Map.of()) as the
                    // initial payload, so the prior `payload == null` sentinel
                    // never matched. Use capturedEnvelope as the "already fired"
                    // marker so the signal fires exactly once on the first call
                    // and the resume call returns done().
                    if (capturedEnvelope.get() == null) {
                        S2cCallbackEnvelope env = envelopeFor(ctx.runId());
                        capturedEnvelope.set(env);
                        throw SuspendSignal.forClientCallback("loop-iter-0", env);
                    }
                    return ExecutorDefinition.ReasoningResult.done("loop-done:" + payload);
                },
                5,
                Map.of());
    }

    @Test
    void happy_path_outcome_ok_propagates_payload_to_resume() {
        AtomicReference<S2cCallbackEnvelope> capturedEnvelope = new AtomicReference<>();
        InMemoryS2cCallbackTransport transport = new InMemoryS2cCallbackTransport(
                env -> S2cCallbackResponse.ok(env.callbackId(), CLIENT_TRACE, "client-result"));
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor())
                .registerS2cCallbackTransport(transport);

        InMemoryRunRegistry runRegistry = new InMemoryRunRegistry();
        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                runRegistry, new InMemoryCheckpointer(), engines);

        UUID runId = UUID.randomUUID();
        Object result = orchestrator.run(runId, "tenant-A", agentThatCallsClient(capturedEnvelope), null);

        assertThat(result).isEqualTo("loop-done:client-result");
        assertThat(runRegistry.findById(runId).orElseThrow().status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(capturedEnvelope.get()).isNotNull();
        assertThat(capturedEnvelope.get().traceId()).hasSize(32);   // W3C 32-char trace per audit §5.2
    }

    @Test
    void callback_id_mismatch_raises_s2c_response_invalid() {
        AtomicReference<S2cCallbackEnvelope> captured = new AtomicReference<>();
        // Transport responds with a DIFFERENT callback id - a tampered or routed-wrong response.
        InMemoryS2cCallbackTransport transport = new InMemoryS2cCallbackTransport(
                env -> S2cCallbackResponse.ok(UUID.randomUUID(), CLIENT_TRACE, "tampered"));
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor())
                .registerS2cCallbackTransport(transport);

        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                new InMemoryRunRegistry(), new InMemoryCheckpointer(), engines);

        assertThatThrownBy(() -> orchestrator.run(UUID.randomUUID(), "tenant-A",
                agentThatCallsClient(captured), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SuspendReason.AwaitClientCallback.S2C_RESPONSE_INVALID);
    }

    @Test
    void outcome_error_raises_s2c_client_error() {
        AtomicReference<S2cCallbackEnvelope> captured = new AtomicReference<>();
        InMemoryS2cCallbackTransport transport = new InMemoryS2cCallbackTransport(
                env -> S2cCallbackResponse.error(env.callbackId(), CLIENT_TRACE, "E001", "client-side failure"));
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor())
                .registerS2cCallbackTransport(transport);

        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                new InMemoryRunRegistry(), new InMemoryCheckpointer(), engines);

        assertThatThrownBy(() -> orchestrator.run(UUID.randomUUID(), "tenant-A",
                agentThatCallsClient(captured), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SuspendReason.AwaitClientCallback.S2C_CLIENT_ERROR);
    }

    @Test
    void missing_transport_raises_s2c_transport_unavailable() {
        AtomicReference<S2cCallbackEnvelope> captured = new AtomicReference<>();
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor());
        // No registerS2cCallbackTransport call -- transport is null.

        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                new InMemoryRunRegistry(), new InMemoryCheckpointer(), engines);

        assertThatThrownBy(() -> orchestrator.run(UUID.randomUUID(), "tenant-A",
                agentThatCallsClient(captured), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s2c_transport_unavailable");
    }
}
