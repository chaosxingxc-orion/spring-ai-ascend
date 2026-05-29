package com.huawei.ascend.service.runtime.s2c;

import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.engine.exec.IterativeAgentLoopExecutor;
import com.huawei.ascend.engine.exec.SequentialGraphExecutor;
import com.huawei.ascend.service.runtime.orchestration.TestEnginePorts;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.middleware.spi.HookOutcome;
import com.huawei.ascend.middleware.spi.HookPoint;
import com.huawei.ascend.middleware.spi.RuntimeMiddleware;
import com.huawei.ascend.bus.spi.engine.SuspendSignal;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Post-review fix (plan C / P0-2): Rule R-M.d declares that S2C invalid-response,
 * client-error, timeout, transport-unavailable, and transport-failure
 * conditions transition the Run to {@link RunStatus#FAILED} and surface as
 * an {@link HookPoint#ON_ERROR} hook fire with a typed {@code reason} attr.
 *
 * <p>Prior to plan C the S2C error path threw {@code IllegalStateException}
 * from inside the (now-removed) {@code catch (S2cCallbackSignal)} branch,
 * leaving the Run stuck in {@link RunStatus#SUSPENDED}. Companion to
 * {@code S2cCallbackRoundTripIT}
 * (E82, which asserts only the happy path + exception-raising half of Rule R-M.d).
 *
 * <p>Enforcer row: {@code docs/governance/enforcers.yaml#E90}.
 *
 * <p>Authority: ADR-0074; CLAUDE.md Rule R-M.d.
 */
class S2cFailureTransitionsRunToFailedIT {

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

    private static List<Map<String, Object>> runAndCaptureOnError(EngineRegistry engines) {
        return runAndCaptureOnError(engines, new InMemoryRunRegistry());
    }

    private static List<Map<String, Object>> runAndCaptureOnError(
            EngineRegistry engines, RunRepository runs) {
        List<Map<String, Object>> onErrorFires = new CopyOnWriteArrayList<>();
        RuntimeMiddleware tap = ctx -> {
            if (ctx.point() == HookPoint.ON_ERROR) {
                onErrorFires.add(Map.copyOf(ctx.attributes()));
            }
            return HookOutcome.proceed();
        };
        engines.registerMiddleware(tap);

        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                runs, new InMemoryCheckpointer(), engines);

        AtomicReference<S2cCallbackEnvelope> captured = new AtomicReference<>();
        UUID runId = UUID.randomUUID();
        // We catch here so the assertion lives in the calling test method.
        try {
            orchestrator.run(runId, "tenant-A", agentThatCallsClient(captured), null);
        } catch (RuntimeException ignored) {
            // Expected — the S2C failure rethrows after finalizing the Run.
        }
        // Make the runId discoverable by callers for assertions on Run state.
        onErrorFires.add(0, Map.of("__runId", runId));
        return onErrorFires;
    }

    @Test
    void s2c_response_invalid_transitions_run_to_failed_with_reason() {
        InMemoryS2cCallbackTransport transport = new InMemoryS2cCallbackTransport(
                env -> S2cCallbackResponse.ok(UUID.randomUUID(), CLIENT_TRACE, "tampered"));
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor())
                .registerS2cCallbackTransport(transport);
        InMemoryRunRegistry runs = new InMemoryRunRegistry();

        List<Map<String, Object>> fires = runAndCaptureOnError(engines, runs);
        UUID runId = (UUID) fires.get(0).get("__runId");

        Run finalRun = runs.findById(runId).orElseThrow();
        assertThat(finalRun.status()).isEqualTo(RunStatus.FAILED);
        assertThat(finalRun.finishedAt()).isNotNull();

        // First non-sentinel hook fire is the ON_ERROR with our reason.
        Map<String, Object> onError = fires.get(1);
        assertThat(onError).containsEntry("reason", "s2c_response_invalid");
        assertThat(onError).containsKey("callbackId");
    }

    @Test
    void s2c_client_error_transitions_run_to_failed_with_reason() {
        InMemoryS2cCallbackTransport transport = new InMemoryS2cCallbackTransport(
                env -> S2cCallbackResponse.error(env.callbackId(), CLIENT_TRACE, "E001", "client-side failure"));
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor())
                .registerS2cCallbackTransport(transport);
        InMemoryRunRegistry runs = new InMemoryRunRegistry();

        List<Map<String, Object>> fires = runAndCaptureOnError(engines, runs);
        UUID runId = (UUID) fires.get(0).get("__runId");

        Run finalRun = runs.findById(runId).orElseThrow();
        assertThat(finalRun.status()).isEqualTo(RunStatus.FAILED);
        assertThat(finalRun.finishedAt()).isNotNull();
        assertThat(fires.get(1)).containsEntry("reason", "s2c_client_error");
    }

    @Test
    void s2c_transport_unavailable_transitions_run_to_failed_with_reason() {
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor());
        // No registerS2cCallbackTransport -- transport is null.
        InMemoryRunRegistry runs = new InMemoryRunRegistry();

        List<Map<String, Object>> fires = runAndCaptureOnError(engines, runs);
        UUID runId = (UUID) fires.get(0).get("__runId");

        Run finalRun = runs.findById(runId).orElseThrow();
        assertThat(finalRun.status()).isEqualTo(RunStatus.FAILED);
        assertThat(finalRun.finishedAt()).isNotNull();
        assertThat(fires.get(1)).containsEntry("reason", "s2c_transport_unavailable");
    }

    @Test
    void s2c_transport_failure_transitions_run_to_failed_with_reason() {
        InMemoryS2cCallbackTransport transport = new InMemoryS2cCallbackTransport(
                env -> {
                    throw new RuntimeException("simulated transport blowup");
                });
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor())
                .registerS2cCallbackTransport(transport);
        InMemoryRunRegistry runs = new InMemoryRunRegistry();

        List<Map<String, Object>> fires = runAndCaptureOnError(engines, runs);
        UUID runId = (UUID) fires.get(0).get("__runId");

        Run finalRun = runs.findById(runId).orElseThrow();
        assertThat(finalRun.status()).isEqualTo(RunStatus.FAILED);
        assertThat(finalRun.finishedAt()).isNotNull();
        assertThat(fires.get(1)).containsEntry("reason", "s2c_transport_failure");
    }

    @Test
    void run_rethrows_so_caller_observes_failure() {
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor());
        // No transport registered -> s2c_transport_unavailable
        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                new InMemoryRunRegistry(), new InMemoryCheckpointer(), engines);

        AtomicReference<S2cCallbackEnvelope> captured = new AtomicReference<>();
        assertThatThrownBy(() -> orchestrator.run(
                UUID.randomUUID(), "tenant-A", agentThatCallsClient(captured), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s2c_transport_unavailable");
    }
}
