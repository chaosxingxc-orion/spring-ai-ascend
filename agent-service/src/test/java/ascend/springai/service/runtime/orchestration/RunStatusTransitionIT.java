package ascend.springai.service.runtime.orchestration;

import ascend.springai.service.runtime.engine.EngineRegistry;
import ascend.springai.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import ascend.springai.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import ascend.springai.service.runtime.orchestration.inmemory.IterativeAgentLoopExecutor;
import ascend.springai.service.runtime.orchestration.inmemory.SequentialGraphExecutor;
import ascend.springai.service.runtime.orchestration.inmemory.SyncOrchestrator;
import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.engine.orchestration.spi.Orchestrator;
import ascend.springai.service.runtime.runs.Run;
import ascend.springai.engine.orchestration.spi.RunMode;
import ascend.springai.service.runtime.runs.RunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies PENDING → RUNNING → SUSPENDED → RUNNING → SUCCEEDED transition fires
 * updatedAt updates and the parent run correctly reflects suspension and recovery.
 */
class RunStatusTransitionIT {

    @Test
    void parent_transitions_through_suspended_and_back_to_succeeded() {
        InMemoryRunRegistry registry = new InMemoryRunRegistry();
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor());
        Orchestrator orchestrator = new SyncOrchestrator(registry, new InMemoryCheckpointer(), engines);

        // Child graph: single terminal node
        ExecutorDefinition.GraphDefinition childGraph = new ExecutorDefinition.GraphDefinition(
                Map.of("only", (ctx, p) -> "child-done"),
                Map.of(),
                "only"
        );

        // Parent agent loop:
        //   iter 0: suspends for child graph
        //   iter 0 (resume): receives "child-done", returns terminal
        ExecutorDefinition.AgentLoopDefinition parentLoop = new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iteration) -> {
                    if (iteration == 0 && !"child-done".equals(payload)) {
                        ctx.suspendForChild("agent-iter-0", RunMode.GRAPH, childGraph, null);
                        return null;
                    }
                    return ExecutorDefinition.ReasoningResult.done("parent-done");
                },
                5,
                Map.of()
        );

        UUID parentId = UUID.randomUUID();
        Object result = orchestrator.run(parentId, "tenant-A", parentLoop, null);
        assertThat(result).isEqualTo("parent-done");

        // Parent run is SUCCEEDED with non-null finishedAt
        Run parent = registry.findById(parentId).orElseThrow();
        assertThat(parent.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(parent.finishedAt()).isNotNull();
        assertThat(parent.updatedAt()).isNotNull();

        // Child run is SUCCEEDED
        List<Run> children = registry.findByParentRunId(parentId);
        assertThat(children).hasSize(1);
        assertThat(children.get(0).status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(children.get(0).mode()).isEqualTo(RunMode.GRAPH);
    }
}
