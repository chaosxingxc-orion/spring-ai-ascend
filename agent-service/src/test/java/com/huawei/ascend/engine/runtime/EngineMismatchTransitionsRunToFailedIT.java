package com.huawei.ascend.engine.runtime;

import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.engine.exec.SequentialGraphExecutor;
import com.huawei.ascend.service.runtime.orchestration.TestEnginePorts;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import com.huawei.ascend.engine.spi.EngineMatchingException;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.middleware.spi.HookOutcome;
import com.huawei.ascend.middleware.spi.HookPoint;
import com.huawei.ascend.middleware.spi.RuntimeMiddleware;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rule R-M.b strongest reading: a Run dispatched with a payload whose engine is
 * not registered MUST raise {@link EngineMatchingException} AND transition
 * the Run to {@link RunStatus#FAILED} with reason {@code engine_mismatch}.
 *
 * <p>The Phase 7 audit (plan {@code D:/.claude/plans/spi-atomic-willow.md} L-1)
 * added the FAILED transition because the prior code only fired the
 * {@link HookPoint#ON_ERROR} hook and rethrew, leaving the Run in its prior
 * status -- a Rule R-M.b compliance gap surfaced by the four-dimensional
 * architecture self-audit. Companion to {@code EngineMatchingStrictnessIT}
 * (E75) which asserts only the exception-raising half of Rule R-M.b.
 *
 * <p>Enforcer row: {@code docs/governance/enforcers.yaml#E88}.
 *
 * <p>Authority: ADR-0072; CLAUDE.md Rule R-M.b.
 */
class EngineMismatchTransitionsRunToFailedIT {

    @Test
    void engine_mismatch_transitions_run_to_failed_and_fires_on_error_with_reason() {
        RunRepository runs = new InMemoryRunRegistry();

        // Capture every ON_ERROR hook context so we can assert the new
        // reason / requestedEngineType / actualPayloadType attributes.
        List<Map<String, Object>> onErrorFires = new CopyOnWriteArrayList<>();
        RuntimeMiddleware tap = ctx -> {
            if (ctx.point() == HookPoint.ON_ERROR) {
                onErrorFires.add(Map.copyOf(ctx.attributes()));
            }
            return HookOutcome.proceed();
        };

        // Registry has only the graph adapter; an agent-loop payload triggers EngineMatchingException.
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .registerMiddleware(tap);

        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                runs,
                new InMemoryCheckpointer(),
                engines);

        UUID runId = UUID.randomUUID();
        ExecutorDefinition.AgentLoopDefinition unregistered = new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iter) -> ExecutorDefinition.ReasoningResult.done("never-reached"),
                1,
                Map.of());

        assertThatThrownBy(() -> orchestrator.run(runId, "tenant-A", unregistered, null))
                .isInstanceOf(EngineMatchingException.class)
                .hasMessageContaining("engine_mismatch");

        // The Run MUST end in FAILED state (the gap before this fix).
        Run finalRun = runs.findById(runId).orElseThrow();
        assertThat(finalRun.status()).isEqualTo(RunStatus.FAILED);
        assertThat(finalRun.finishedAt()).isNotNull();

        // ON_ERROR hook fired carrying reason=engine_mismatch + engine attrs.
        assertThat(onErrorFires).isNotEmpty();
        Map<String, Object> firstFire = onErrorFires.get(0);
        assertThat(firstFire).containsEntry("reason", "engine_mismatch");
        assertThat(firstFire).containsKey("requestedEngineType");
        assertThat(firstFire).containsKey("actualPayloadType");
        assertThat(firstFire.get("actualPayloadType")).isEqualTo("AgentLoopDefinition");
    }
}
