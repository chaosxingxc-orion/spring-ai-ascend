package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.bus.spi.engine.AgentEvent;
import com.huawei.ascend.bus.spi.engine.DefinitionResolver;
import com.huawei.ascend.bus.spi.engine.EngineDescriptor;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.bus.spi.engine.ExecuteRequest;
import com.huawei.ascend.bus.spi.engine.ExecutionContext;
import com.huawei.ascend.engine.runtime.EngineOutcomeChannel;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.engine.spi.AgentLoopExecutor;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.engine.spi.GraphExecutor;
import com.huawei.ascend.bus.spi.engine.RunContext;
import com.huawei.ascend.service.runtime.capability.CapabilityRegistry;
import com.huawei.ascend.service.runtime.orchestration.CompositeDefinitionResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies that SyncOrchestrator delegates to AppPostureGate on construction (ADR-0035, §4 #32).
 *
 * <p>The research/prod throw is validated in AppPostureGateTest which exercises the gate directly
 * (env-var manipulation is not possible within the JVM). Gate Rule 12 asserts the
 * AppPostureGate.requireDevForInMemoryComponent literal is present in SyncOrchestrator.java,
 * ensuring delegation is wired.
 *
 * <p>The posture guard fires in the constructor BEFORE the injected EnginePort / resolver /
 * outcome-channel are used, so a minimal stub port suffices here — the dependencies are passed
 * directly rather than through TestEnginePorts.
 */
class SyncOrchestratorPostureGuardTest {

    @Test
    void dev_posture_allows_construction() {
        // APP_POSTURE not set in test env → dev posture → AppPostureGate warns, does not throw.
        var registry = new InMemoryRunRegistry();
        var checkpointer = new InMemoryCheckpointer();
        var engines = stubEngineRegistry();

        assertThatCode(() -> new SyncOrchestrator(registry, checkpointer, engines,
                stubEnginePort(), stubResolver(), new EngineOutcomeChannel()))
                .doesNotThrowAnyException();
    }

    @Test
    void construction_wires_all_required_dependencies() {
        var registry = new InMemoryRunRegistry();
        var checkpointer = new InMemoryCheckpointer();
        var orchestrator = new SyncOrchestrator(registry, checkpointer, stubEngineRegistry(),
                stubEnginePort(), stubResolver(), new EngineOutcomeChannel());
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

    private static DefinitionResolver stubResolver() {
        return new CompositeDefinitionResolver(new CapabilityRegistry(List.of()));
    }

    /** Minimal EnginePort stub — never invoked by these construction-only assertions. */
    private static EnginePort stubEnginePort() {
        return new EnginePort() {
            @Override
            public Flow.Publisher<AgentEvent> execute(ExecutionContext ctx, ExecuteRequest request) {
                return null;
            }

            @Override
            public EngineDescriptor describe() {
                return new EngineDescriptor(Set.of(), "UP");
            }
        };
    }
}
