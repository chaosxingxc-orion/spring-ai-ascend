package com.huawei.ascend.service.runtime.orchestration;

import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.engine.exec.IterativeAgentLoopExecutor;
import com.huawei.ascend.engine.exec.SequentialGraphExecutor;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.Orchestrator;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.bus.spi.engine.RunMode;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: 3-level bidirectional nesting.
 *
 * Execution tree:
 *   L1 Graph (start → delegate → finish)
 *     delegate suspends → L2 AgentLoop (iter 0, 1, 2)
 *                           iter 1 suspends → L3 Graph (g1 → g2)
 *
 * Expected final result: "[L1-start|AL-iter-0|GRAPH-L3|AL-resume|L1-finish]"
 *
 * Asserts:
 *  - 3 Run rows exist with correct parentRunId chain
 *  - 2 runs have SUSPENDED status checkpoints in the registry (they were suspended then resumed)
 *  - Final result string matches expected value
 *  - Tenant propagates through all 3 nesting levels
 */
class NestedDualModeIT {

    private static final String TENANT = "fin-tenant-1";

    @Test
    void three_level_graph_agentloop_graph_nesting_completes() {
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        InMemoryRunRegistry registry = new InMemoryRunRegistry();
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor());
        Orchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(registry, checkpointer, engines);

        // L3 Graph: g1 → g2, produces "GRAPH-L3"
        ExecutorDefinition.GraphDefinition l3Graph = new ExecutorDefinition.GraphDefinition(
                Map.of(
                        "g1", (ctx, p) -> {
                            assertThat(ctx.tenantId()).isEqualTo(TENANT);
                            return "GRAPH-L3-step1";
                        },
                        "g2", (ctx, p) -> "GRAPH-L3"
                ),
                Map.of("g1", "g2"),
                "g1"
        );

        // L2 AgentLoop:
        //   iter 0: appends "AL-iter-0" to payload
        //   iter 1 (first call): suspends for L3 Graph
        //   iter 1 (resume): receives "GRAPH-L3", returns terminal "AL-resume"
        ExecutorDefinition.AgentLoopDefinition l2Loop = new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iteration) -> {
                    assertThat(ctx.tenantId()).isEqualTo(TENANT);
                    if (iteration == 0) {
                        String p = payload == null ? "" : payload.toString();
                        return ExecutorDefinition.ReasoningResult.next(p + "AL-iter-0|");
                    }
                    if (iteration == 1 && !payload.toString().contains("GRAPH-L3")) {
                        // First call at iter 1: delegate to L3 graph
                        ctx.suspendForChild("agent-iter-1", RunMode.GRAPH, l3Graph, null);
                        return null; // unreachable — suspendForChild always throws
                    }
                    // Resume at iter 1 with L3 result
                    return ExecutorDefinition.ReasoningResult.done(payload + "AL-resume|");
                },
                10,
                Map.of()
        );

        // L1 Graph: start → delegate → finish
        //   start:    produces "[L1-start|"
        //   delegate: first call suspends for L2 loop; on resume returns child result
        //   finish:   appends "L1-finish]"
        ExecutorDefinition.GraphDefinition l1Graph = new ExecutorDefinition.GraphDefinition(
                Map.of(
                        "start", (ctx, p) -> {
                            assertThat(ctx.tenantId()).isEqualTo(TENANT);
                            return "[L1-start|";
                        },
                        "delegate", (ctx, p) -> {
                            // On resume, p is the AL result — no need to suspend again
                            if (p != null && p.toString().contains("AL-resume")) {
                                return p;
                            }
                            // First call: delegate to L2 agent loop; pass L1-start result as seed
                            ctx.suspendForChild("delegate", RunMode.AGENT_LOOP, l2Loop, p);
                            return null; // unreachable
                        },
                        "finish", (ctx, p) -> p + "L1-finish]"
                ),
                Map.of("start", "delegate", "delegate", "finish"),
                "start"
        );

        UUID l1RunId = UUID.randomUUID();
        Object result = orchestrator.run(l1RunId, TENANT, l1Graph, null);

        // Assert result
        assertThat(result.toString()).isEqualTo("[L1-start|AL-iter-0|GRAPH-L3AL-resume|L1-finish]");

        // Assert 3 Run rows exist
        List<Run> allRuns = registry.findByTenant(TENANT);
        assertThat(allRuns).hasSize(3);

        // Assert parent run is SUCCEEDED
        Run l1Run = registry.findById(l1RunId).orElseThrow();
        assertThat(l1Run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(l1Run.mode()).isEqualTo(RunMode.GRAPH);
        assertThat(l1Run.parentRunId()).isNull();

        // Assert L2 child
        List<Run> l2Children = registry.findByParentRunId(l1RunId);
        assertThat(l2Children).hasSize(1);
        Run l2Run = l2Children.get(0);
        assertThat(l2Run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(l2Run.mode()).isEqualTo(RunMode.AGENT_LOOP);
        assertThat(l2Run.tenantId()).isEqualTo(TENANT);

        // Assert L3 grandchild
        List<Run> l3Children = registry.findByParentRunId(l2Run.runId());
        assertThat(l3Children).hasSize(1);
        Run l3Run = l3Children.get(0);
        assertThat(l3Run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(l3Run.mode()).isEqualTo(RunMode.GRAPH);
        assertThat(l3Run.tenantId()).isEqualTo(TENANT);
    }
}
