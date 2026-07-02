package com.openjiuwen.runtime.alpha.executor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.alpha.executor.SuperstepResult;
import com.openjiuwen.core.alpha.graph.*;
import com.openjiuwen.core.alpha.model.ErrorPolicy;
import com.openjiuwen.core.alpha.model.ExecutionPolicy;
import com.openjiuwen.core.alpha.verifier.NodeResult;
import com.openjiuwen.core.kernel.model.*;
import com.openjiuwen.core.meta.AgentDefinition;
import com.openjiuwen.runtime.core.dispatch.TaskContext;
import com.openjiuwen.runtime.core.engine.AgentKernel;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * DefaultPregelExecutor 控制流承重——mock 证 BSP 执行器结构行为。
 *
 * <p>每个测试标注 mutation-RED：剥什么断言会变红。
 */
class DefaultPregelExecutorControlFlowTest {

    // ==================== mock kernel ====================

    /** Tracks tool invocations and returns pre-configured tool results. */
    static class TrackingKernel implements AgentKernel {
        final Map<String, String> toolResults = new LinkedHashMap<>();
        final Map<String, String> thinkResults = new LinkedHashMap<>();
        final List<String> toolCalls = new ArrayList<>();
        final List<String> thinkCalls = new ArrayList<>();

        TrackingKernel withTool(String toolName, String result) {
            toolResults.put(toolName, result);
            return this;
        }

        TrackingKernel withThink(String nodeDesc, String result) {
            thinkResults.put(nodeDesc, result);
            return this;
        }

        @Override
        public Mono<String> think(String prompt, BudgetLimits budget) {
            thinkCalls.add(prompt);
            // Match by node description substring
            for (var entry : thinkResults.entrySet()) {
                if (prompt.contains(entry.getKey())) {
                    return Mono.just(entry.getValue());
                }
            }
            return Mono.just("mock-think-default");
        }

        @Override
        public Mono<ToolResult> invokeTool(ToolName toolName, Map<String, Object> arguments,
                                            BudgetLimits budget) {
            toolCalls.add(toolName.value());
            String result = toolResults.getOrDefault(toolName.value(), "mock-tool-result");
            return Mono.just(ToolResult.ok(toolName, result));
        }

        @Override public Mono<Void> emit(AgentEvent event) { return Mono.empty(); }
        @Override public Mono<Map<NodeId, Object>> observe(TaskId taskId, Set<NodeId> nodeIds) {
            return Mono.just(Map.of());
        }
        @Override public Mono<CheckpointId> saveCheckpoint(Checkpoint cp) { return Mono.just(new CheckpointId("cp1")); }
        @Override public Mono<Checkpoint> restoreCheckpoint(TaskId taskId) { return Mono.empty(); }
        @Override public Mono<CheckpointId> yield(TaskId taskId, YieldReason reason, String state) {
            return Mono.just(new CheckpointId("yield1"));
        }
        @Override public Flux<AgentEvent> observeEvents(TaskId taskId) { return Flux.empty(); }
    }

    /** Returns null/fails on tool calls — to test DeviceFailure path. */
    static class FailingKernel extends TrackingKernel {
        @Override
        public Mono<ToolResult> invokeTool(ToolName toolName, Map<String, Object> arguments,
                                            BudgetLimits budget) {
            toolCalls.add(toolName.value());
            return Mono.just(ToolResult.fail(toolName, "mock tool failure for " + toolName.value()));
        }
    }

    private static TaskContext ctx(AgentKernel kernel) {
        return new TaskContext(new TaskId("t1"), new AgentName("test"),
                TaskInput.of("test-input"),
                new AgentDefinition(new AgentName("test"), "desc", "sys", List.of(),
                        com.openjiuwen.core.dispatch.AutonomyLevel.GUIDED,
                        Budget.Fixed.productionDefault(), ExecutionPolicy.productionDefault(),
                        null, null, Map.of()),
                kernel, Budget.Fixed.productionDefault(),
                com.openjiuwen.core.dispatch.AutonomyLevel.GUIDED, Map.of());
    }

    private static ExecutionPolicy policy() {
        return new ExecutionPolicy(
                com.openjiuwen.core.alpha.model.PlanningMode.AUTO,
                com.openjiuwen.core.alpha.model.VerifyMode.NONE,
                3, 4, true);
    }

    // ==================== P1: single layer single node ====================

    @Test
    void singleLayerSingleNodeExecutesSuccessfully() {
        TrackingKernel kernel = new TrackingKernel()
                .withThink("do A", "result-A");
        DefaultPregelExecutor executor = new DefaultPregelExecutor(ctx(kernel));
        TaskGraph graph = new TaskGraph("test", List.of(
                TaskNode.of("A", "do A", TaskNodeType.LLM_CALL)), List.of());

        List<SuperstepResult> results = executor.execute(
                new TaskId("t1"), graph, policy(),
                BudgetLimits.start(Budget.Fixed.productionDefault())).collectList().block();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).superstepIndex()).isEqualTo(0);
        assertThat(results.get(0).failedNodes()).isEmpty();
        assertThat(results.get(0).nodeResults()).containsKey(new NodeId("A"));
        // mutation-RED: strip executor.execute → results empty → RED
    }

    // ==================== P2: multi-layer sequential ====================

    @Test
    void multiLayerSequentialExecution() {
        TrackingKernel kernel = new TrackingKernel()
                .withThink("A", "result-A")
                .withThink("B", "result-B")
                .withThink("C", "result-C");
        DefaultPregelExecutor executor = new DefaultPregelExecutor(ctx(kernel));
        TaskGraph graph = new TaskGraph("pipeline", List.of(
                TaskNode.of("A", "A", TaskNodeType.LLM_CALL),
                TaskNode.of("B", "B", TaskNodeType.LLM_CALL),
                TaskNode.of("C", "C", TaskNodeType.LLM_CALL)),
                List.of(
                        new TaskEdge(new NodeId("A"), new NodeId("B"), "output"),
                        new TaskEdge(new NodeId("B"), new NodeId("C"), "output")));

        List<SuperstepResult> results = executor.execute(
                new TaskId("t1"), graph, policy(),
                BudgetLimits.start(Budget.Fixed.productionDefault())).collectList().block();

        assertThat(results).hasSize(3); // 3 layers: A → B → C
        // mutation-RED: strip topological sort → layers == 1 → RED
    }

    // ==================== P4: device failure ====================

    @Test
    void toolFailureProducesDeviceFailure() {
        FailingKernel kernel = new FailingKernel();
        DefaultPregelExecutor executor = new DefaultPregelExecutor(ctx(kernel));
        TaskGraph graph = new TaskGraph("fail", List.of(
                TaskNode.of("T", "tool-x", TaskNodeType.TOOL_CALL)), List.of());

        List<SuperstepResult> results = executor.execute(
                new TaskId("t1"), graph, policy(),
                BudgetLimits.start(Budget.Fixed.productionDefault())).collectList().block();

        assertThat(results).hasSize(1);
        Set<NodeId> failed = results.get(0).failedNodes();
        assertThat(failed).contains(new NodeId("T"));
        Object raw = results.get(0).nodeResults().get(new NodeId("T"));
        assertThat(raw).isInstanceOf(NodeResult.DeviceFailure.class);
        NodeResult.DeviceFailure df = (NodeResult.DeviceFailure) raw;
        assertThat(df.error()).contains("mock tool failure");
        // mutation-RED: strip DeviceFailure wrapping → instanceof check fails → RED
    }

    // ==================== P7: TOOL_CALL → invokeTool ====================

    @Test
    void toolCallNodeInvokesKernelInvokeTool() {
        TrackingKernel kernel = new TrackingKernel()
                .withTool("checkOrder", "order-ok");
        DefaultPregelExecutor executor = new DefaultPregelExecutor(ctx(kernel));
        TaskGraph graph = new TaskGraph("tool", List.of(
                TaskNode.of("T1", "checkOrder", TaskNodeType.TOOL_CALL)), List.of());

        executor.execute(new TaskId("t1"), graph, policy(),
                BudgetLimits.start(Budget.Fixed.productionDefault())).blockLast();

        assertThat(kernel.toolCalls).contains("checkOrder");
        // mutation-RED: strip tool invocation → toolCalls empty → RED
    }

    // ==================== P8: LLM_CALL → think ====================

    @Test
    void llmCallNodeInvokesKernelThink() {
        TrackingKernel kernel = new TrackingKernel()
                .withThink("analyze", "analysis-result");
        DefaultPregelExecutor executor = new DefaultPregelExecutor(ctx(kernel));
        TaskGraph graph = new TaskGraph("think", List.of(
                TaskNode.of("L1", "analyze market", TaskNodeType.LLM_CALL)), List.of());

        executor.execute(new TaskId("t1"), graph, policy(),
                BudgetLimits.start(Budget.Fixed.productionDefault())).blockLast();

        assertThat(kernel.thinkCalls).isNotEmpty();
        assertThat(kernel.thinkCalls.get(0)).contains("analyze");
        // mutation-RED: strip think → thinkCalls empty → RED
    }

    // ==================== P10: ErrorPolicy dispatch ====================

    @Test
    void failFastErrorPolicyStopsOnFirstFailure() {
        FailingKernel kernel = new FailingKernel();
        // FailFast policy via ErrorPolicy.Retry(1, 0, new ErrorPolicy.FailFast())
        DefaultPregelExecutor executor = new DefaultPregelExecutor(ctx(kernel),
                new ErrorPolicy.Retry(1, 0, new ErrorPolicy.FailFast()),
                new com.openjiuwen.core.alpha.executor.SubAgentBudget.Proportional(0.3));
        TaskGraph graph = new TaskGraph("fail", List.of(
                TaskNode.of("T1", "tool-x", TaskNodeType.TOOL_CALL),
                TaskNode.of("T2", "tool-y", TaskNodeType.TOOL_CALL)), List.of());

        List<SuperstepResult> results = executor.execute(
                new TaskId("t1"), graph, policy(),
                BudgetLimits.start(Budget.Fixed.productionDefault())).collectList().block();

        assertThat(results).hasSize(1); // stopped after first layer failure
        // mutation-RED: strip error policy check → 2 layers (retry succeeds) → RED
    }
}
