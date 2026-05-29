package com.huawei.ascend.engine.runtime;

import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.engine.exec.IterativeAgentLoopExecutor;
import com.huawei.ascend.engine.exec.SequentialGraphExecutor;
import com.huawei.ascend.service.runtime.orchestration.TestEnginePorts;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import com.huawei.ascend.engine.spi.EngineMatchingException;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end strict-matching: a Run dispatched through SyncOrchestrator with a
 * payload class for which no adapter is registered raises
 * {@link EngineMatchingException} — the runtime never silently reinterprets
 * the payload as another engine's configuration (Rule R-M.b, ADR-0072 strongest
 * reading: no fallback policy).
 *
 * <p>Enforcer row: {@code docs/governance/enforcers.yaml#E75}.
 */
class EngineMatchingStrictnessIT {

    @Test
    void payload_with_no_registered_adapter_raises_engine_mismatch() {
        // Registry has only the graph adapter; the Run will submit an agent-loop payload.
        EngineRegistry engines = new EngineRegistry().register(new SequentialGraphExecutor());
        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                new InMemoryRunRegistry(),
                new InMemoryCheckpointer(),
                engines);

        ExecutorDefinition.AgentLoopDefinition unregisteredPayload =
                new ExecutorDefinition.AgentLoopDefinition(
                        (ctx, payload, iter) -> ExecutorDefinition.ReasoningResult.done("done"),
                        1,
                        Map.of());

        assertThatThrownBy(() -> orchestrator.run(UUID.randomUUID(), "tenant-A", unregisteredPayload, null))
                .isInstanceOf(EngineMatchingException.class)
                .hasMessageContaining("engine_mismatch")
                .hasMessageContaining("AgentLoopDefinition");
    }

    @Test
    void registered_adapters_dispatch_normally() {
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor());
        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                new InMemoryRunRegistry(),
                new InMemoryCheckpointer(),
                engines);

        ExecutorDefinition.GraphDefinition graph =
                new ExecutorDefinition.GraphDefinition(
                        Map.of("only", (ctx, p) -> "graph-ok"),
                        Map.of(),
                        "only");

        Object result = orchestrator.run(UUID.randomUUID(), "tenant-A", graph, null);

        assertThat(result).isEqualTo("graph-ok");
    }
}
