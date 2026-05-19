package ascend.springai.service.runtime.engine;

import ascend.springai.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import ascend.springai.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import ascend.springai.service.runtime.orchestration.inmemory.IterativeAgentLoopExecutor;
import ascend.springai.service.runtime.orchestration.inmemory.SequentialGraphExecutor;
import ascend.springai.service.runtime.orchestration.inmemory.SyncOrchestrator;
import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.middleware.spi.HookOutcome;
import ascend.springai.middleware.spi.HookPoint;
import ascend.springai.middleware.spi.RuntimeMiddleware;
import ascend.springai.engine.orchestration.spi.RunMode;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end verification that SyncOrchestrator fires the three Phase 2
 * mandatory hooks ({@link HookPoint#ON_ERROR},
 * {@link HookPoint#BEFORE_SUSPENSION}, {@link HookPoint#BEFORE_RESUME})
 * through registered {@link RuntimeMiddleware} instances.
 *
 * <p>Authority: ADR-0073; CLAUDE.md Rule R-M.c.
 * Enforcer row: {@code docs/governance/enforcers.yaml#E80}.
 */
class RuntimeMiddlewareInterceptsHooksIT {

    @Test
    void suspend_resume_cycle_fires_before_suspension_then_before_resume() {
        Set<HookPoint> seen = ConcurrentHashMap.newKeySet();
        RuntimeMiddleware tap = ctx -> {
            seen.add(ctx.point());
            return HookOutcome.proceed();
        };

        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor())
                .registerMiddleware(tap);

        SyncOrchestrator orchestrator = new SyncOrchestrator(
                new InMemoryRunRegistry(),
                new InMemoryCheckpointer(),
                engines);

        ExecutorDefinition.GraphDefinition childGraph = new ExecutorDefinition.GraphDefinition(
                Map.of("only", (ctx, p) -> "child-done"),
                Map.of(),
                "only");
        ExecutorDefinition.AgentLoopDefinition parentLoop = new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iter) -> {
                    if (iter == 0 && !"child-done".equals(payload)) {
                        ctx.suspendForChild("p0", RunMode.GRAPH, childGraph, null);
                        return null;
                    }
                    return ExecutorDefinition.ReasoningResult.done("parent-done");
                },
                10,
                Map.of());

        Object result = orchestrator.run(UUID.randomUUID(), "tenant-A", parentLoop, null);

        assertThat(result).isEqualTo("parent-done");
        assertThat(seen).contains(HookPoint.BEFORE_SUSPENSION, HookPoint.BEFORE_RESUME);
        // Full successful run never enters on_error.
        assertThat(seen).doesNotContain(HookPoint.ON_ERROR);
    }

    @Test
    void unhandled_runtime_exception_fires_on_error_then_propagates() {
        Set<HookPoint> seen = EnumSet.noneOf(HookPoint.class);
        RuntimeMiddleware tap = ctx -> {
            seen.add(ctx.point());
            return HookOutcome.proceed();
        };

        // Registry has only the graph adapter; an agent-loop payload triggers EngineMatchingException.
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .registerMiddleware(tap);

        SyncOrchestrator orchestrator = new SyncOrchestrator(
                new InMemoryRunRegistry(),
                new InMemoryCheckpointer(),
                engines);

        ExecutorDefinition.AgentLoopDefinition unregistered = new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iter) -> ExecutorDefinition.ReasoningResult.done("done"),
                1,
                Map.of());

        assertThatThrownBy(() -> orchestrator.run(UUID.randomUUID(), "tenant-A", unregistered, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("engine_mismatch");

        assertThat(seen).contains(HookPoint.ON_ERROR);
    }
}
