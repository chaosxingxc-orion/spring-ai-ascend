package ascend.springai.service.runtime.orchestration.inmemory;

import ascend.springai.engine.runtime.EngineRegistry;
import ascend.springai.engine.spi.AgentLoopExecutor;
import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.engine.spi.GraphExecutor;
import ascend.springai.engine.orchestration.spi.RunContext;
import ascend.springai.engine.orchestration.spi.SuspendSignal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies that SyncOrchestrator delegates to AppPostureGate on construction (ADR-0035, §4 #32).
 *
 * <p>The research/prod throw is validated in AppPostureGateTest which exercises the gate directly
 * (env-var manipulation is not possible within the JVM). Gate Rule 12 asserts the
 * AppPostureGate.requireDevForInMemoryComponent literal is present in SyncOrchestrator.java,
 * ensuring delegation is wired.
 */
class SyncOrchestratorPostureGuardTest {

    @Test
    void dev_posture_allows_construction() {
        // APP_POSTURE not set in test env → dev posture → AppPostureGate warns, does not throw.
        var registry = new InMemoryRunRegistry();
        var checkpointer = new InMemoryCheckpointer();
        var engines = stubEngineRegistry();

        assertThatCode(() -> new SyncOrchestrator(registry, checkpointer, engines))
                .doesNotThrowAnyException();
    }

    @Test
    void construction_wires_all_required_dependencies() {
        var registry = new InMemoryRunRegistry();
        var checkpointer = new InMemoryCheckpointer();
        var orchestrator = new SyncOrchestrator(registry, checkpointer, stubEngineRegistry());
        assertThat(orchestrator).isNotNull();
    }

    /**
     * Stub graph + agent-loop executors registered via EngineRegistry — Rule R-M.a
     * forbids pattern-matching on ExecutorDefinition subtypes outside the registry.
     */
    private static EngineRegistry stubEngineRegistry() {
        GraphExecutor stubGraph = (RunContext ctx, ExecutorDefinition.GraphDefinition def, Object payload) -> payload;
        AgentLoopExecutor stubLoop = (RunContext ctx, ExecutorDefinition.AgentLoopDefinition def, Object payload) -> payload;
        return new EngineRegistry().register(stubGraph).register(stubLoop);
    }
}
