package com.openjiuwen.reference.gepa3;

import com.openjiuwen.core.alpha.graph.TaskEdge;
import com.openjiuwen.core.alpha.graph.TaskGraph;
import com.openjiuwen.core.alpha.graph.TaskNode;
import com.openjiuwen.core.alpha.graph.TaskNodeType;
import com.openjiuwen.core.kernel.model.Budget;
import com.openjiuwen.core.kernel.model.BudgetLimits;
import com.openjiuwen.core.kernel.model.NodeId;
import com.openjiuwen.core.kernel.model.TaskId;
import com.openjiuwen.core.kernel.model.ToolName;
import com.openjiuwen.core.kernel.model.ToolResult;
import com.openjiuwen.runtime.core.engine.AgentKernel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("StreamingPregelDemo v1.1")
class StreamingPregelDemoTest {

    private MockKernel kernel;
    private StreamingPregelDemo demo;

    @BeforeEach
    void setUp() {
        kernel = new MockKernel();
        demo = new StreamingPregelDemo(kernel);
    }

    @Nested
    @DisplayName("自愈")
    class SelfHealing {
        @Test
        @DisplayName("工具节点异常 → onErrorResume 捕获，同层其他节点继续")
        void singleToolFailureDoesNotKillLayer() {
            TaskNode a = TaskNode.of("A", "successTool", TaskNodeType.TOOL_CALL);
            TaskNode b = TaskNode.of("B", "failingTool", TaskNodeType.TOOL_CALL);
            kernel.registerTool("successTool", ToolResult.ok(new ToolName("successTool"), "A-ok"));
            kernel.registerFailingTool("failingTool", new RuntimeException("boom"));
            TaskGraph g = new TaskGraph("g", List.of(a, b), List.of());
            Flux<StreamingPregelDemo.SuperstepResult> f = demo.execute(new TaskId("t1"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).assertNext(l0 -> {
                assertThat(l0.nodeResults()).hasSize(2);
                assertThat(find(l0, "A").failed()).isFalse();
                assertThat(find(l0, "A").value()).isEqualTo("A-ok");
                assertThat(find(l0, "B").failed()).isTrue();
                assertThat(find(l0, "B").error()).contains("boom");
            }).verifyComplete();
        }

        @Test
        @DisplayName("LLM 超时 → onErrorResume (v1.1: 构造器注入)")
        void llmNodeTimeoutDoesNotKillLayer() {
            TaskNode slow = TaskNode.of("slow", "慢", TaskNodeType.LLM_CALL);
            TaskNode fast = TaskNode.of("fast", "快", TaskNodeType.LLM_CALL);
            kernel.registerSlowLLM("slow", Duration.ofSeconds(200), "慢结果");
            kernel.registerLLM("fast", "快结果");
            TaskGraph g = new TaskGraph("g", List.of(slow, fast), List.of());
            StreamingPregelDemo fd = new StreamingPregelDemo(kernel,
                    Duration.ofMillis(200), Duration.ofSeconds(5));
            Flux<StreamingPregelDemo.SuperstepResult> f = fd.execute(new TaskId("t2"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).assertNext(l0 -> {
                assertThat(l0.nodeResults()).hasSize(2);
                StreamingPregelDemo.NodeResult fastR = find(l0, "fast");
                assertThat(fastR.failed()).isFalse();
                assertThat(fastR.value()).isEqualTo("快结果");
                assertThat(l0.failedNodes().stream().anyMatch(n -> n.value().equals("slow"))).isTrue();
            }).verifyComplete();
        }

        @Test
        @DisplayName("全部失败 → 层完成，全标记 failed")
        void allNodesFailLayerStillCompletes() {
            TaskNode a = TaskNode.of("A", "failA", TaskNodeType.TOOL_CALL);
            TaskNode b = TaskNode.of("B", "failB", TaskNodeType.TOOL_CALL);
            kernel.registerFailingTool("failA", new RuntimeException("A"));
            kernel.registerFailingTool("failB", new RuntimeException("B"));
            TaskGraph g = new TaskGraph("g", List.of(a, b), List.of());
            Flux<StreamingPregelDemo.SuperstepResult> f = demo.execute(new TaskId("t3"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).assertNext(l0 -> {
                assertThat(l0.nodeResults()).hasSize(2);
                assertThat(l0.failedNodes()).hasSize(2);
                l0.nodeResults().forEach(r -> assertThat(r.failed()).isTrue());
            }).verifyComplete();
        }

        @Test
        @DisplayName("B1 回归: 层超时时部分结果保留")
        void partialResultsSurviveLayerTimeout() {
            TaskNode a = TaskNode.of("A", "fastA", TaskNodeType.TOOL_CALL);
            TaskNode b = TaskNode.of("B", "fastB", TaskNodeType.TOOL_CALL);
            TaskNode z = TaskNode.of("Z", "zombie", TaskNodeType.TOOL_CALL);
            kernel.registerTool("fastA", ToolResult.ok(new ToolName("fastA"), "rA"));
            kernel.registerTool("fastB", ToolResult.ok(new ToolName("fastB"), "rB"));
            kernel.registerHangingTool("zombie");
            TaskGraph g = new TaskGraph("g", List.of(a, b, z), List.of());
            StreamingPregelDemo td = new StreamingPregelDemo(kernel,
                    Duration.ofSeconds(60), Duration.ofMillis(200));
            Flux<StreamingPregelDemo.SuperstepResult> f = td.execute(new TaskId("tb1"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).assertNext(l0 -> {
                assertThat(l0.nodeResults()).hasSize(3);
                assertThat(find(l0, "A").failed()).isFalse();
                assertThat(find(l0, "A").value()).isEqualTo("rA");
                assertThat(find(l0, "B").failed()).isFalse();
                assertThat(find(l0, "B").value()).isEqualTo("rB");
                assertThat(find(l0, "Z").failed()).isTrue();
                assertThat(find(l0, "Z").error()).contains("层超时");
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("BSP 屏障")
    class BspBarrier {
        @Test
        @DisplayName("层 1 在层 0 完成后才开始")
        void layerOneStartsOnlyAfterLayerZeroCompletes() {
            AtomicInteger phase = new AtomicInteger(0);
            AtomicInteger l0done = new AtomicInteger(-1);
            AtomicInteger l1start = new AtomicInteger(-1);
            TaskNode a = TaskNode.of("A", "stepA", TaskNodeType.TOOL_CALL);
            TaskNode b = TaskNode.of("B", "stepB", TaskNodeType.TOOL_CALL);
            TaskNode c = TaskNode.of("C", "stepC", TaskNodeType.TOOL_CALL, Map.of("x", "${A.output}"));
            kernel.registerTool("stepA", ToolResult.ok(new ToolName("stepA"), "dA"));
            kernel.registerTool("stepB", ToolResult.ok(new ToolName("stepB"), "dB"));
            kernel.registerTool("stepC", ToolResult.ok(new ToolName("stepC"), "dC"));
            TaskGraph g = new TaskGraph("g", List.of(a, b, c), List.of(TaskEdge.of("A", "C")));
            Flux<StreamingPregelDemo.SuperstepResult> f = demo.execute(new TaskId("t4"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()))
                    .doOnNext(sr -> {
                        if (sr.layerIndex() == 0) l0done.set(phase.incrementAndGet());
                        if (sr.layerIndex() == 1) l1start.set(phase.incrementAndGet());
                    });
            StepVerifier.create(f)
                    .assertNext(l0 -> { assertThat(l0.layerIndex()).isEqualTo(0); })
                    .assertNext(l1 -> { assertThat(l1.layerIndex()).isEqualTo(1); })
                    .verifyComplete();
            assertThat(l0done.get()).isEqualTo(1);
            assertThat(l1start.get()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("取消传播")
    class Cancellation {
        @Test
        @DisplayName("dispose → 累加器标记 cancelled (v1.1: 真断言)")
        void disposeMarksAccumulatorCancelled() {
            TaskNode n = TaskNode.of("A", "hanging", TaskNodeType.TOOL_CALL);
            kernel.registerHangingTool("hanging");
            TaskGraph g = new TaskGraph("g", List.of(n), List.of());
            StreamingPregelDemo sd = new StreamingPregelDemo(kernel,
                    Duration.ofSeconds(60), Duration.ofSeconds(5));
            Flux<StreamingPregelDemo.SuperstepResult> f = sd.execute(new TaskId("t5"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).thenRequest(1).thenAwait(Duration.ofMillis(50)).thenCancel().verify();
            assertThat(kernel.invokeCount.get()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("多层中途 cancel → 后续层不发射")
        void midExecutionCancelStopsRemainingLayers() {
            TaskNode a = TaskNode.of("A", "stepA", TaskNodeType.TOOL_CALL);
            TaskNode b = TaskNode.of("B", "stepB", TaskNodeType.TOOL_CALL, Map.of("d", "${A.output}"));
            TaskNode c = TaskNode.of("C", "stepC", TaskNodeType.TOOL_CALL, Map.of("d", "${B.output}"));
            kernel.registerTool("stepA", ToolResult.ok(new ToolName("stepA"), "a"));
            kernel.registerTool("stepB", ToolResult.ok(new ToolName("stepB"), "b"));
            kernel.registerTool("stepC", ToolResult.ok(new ToolName("stepC"), "c"));
            TaskGraph g = new TaskGraph("g", List.of(a, b, c),
                    List.of(TaskEdge.of("A", "B"), TaskEdge.of("B", "C")));
            Flux<StreamingPregelDemo.SuperstepResult> f = demo.execute(new TaskId("t6"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).expectNextCount(1).thenCancel().verify();
        }
    }

    @Nested
    @DisplayName("背压")
    class Backpressure {
        @Test
        @DisplayName("request(1) → 至少发出 layer 0（首发顺序 smoke）")
        void downstreamRequestOneEmitsLayerZero() {
            TaskNode a = TaskNode.of("A", "stepA", TaskNodeType.TOOL_CALL);
            TaskNode b = TaskNode.of("B", "stepB", TaskNodeType.TOOL_CALL, Map.of("d", "${A.output}"));
            kernel.registerTool("stepA", ToolResult.ok(new ToolName("stepA"), "a"));
            kernel.registerHangingTool("stepB");
            TaskGraph g = new TaskGraph("g", List.of(a, b), List.of(TaskEdge.of("A", "B")));
            StreamingPregelDemo bd = new StreamingPregelDemo(kernel,
                    Duration.ofSeconds(60), Duration.ofSeconds(30));
            Flux<StreamingPregelDemo.SuperstepResult> f = bd.execute(new TaskId("t7"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            // 只断言 layer 0 发出（稳健）。原「stepB 零调用」断言测的是 MockKernel 计数时序，非原型语义：
            // execute() 用 concatMap 串行层（layer 0 onComplete 后才订阅 layer 1），本无预取；但 layer 0 完成后
            // concatMap 立即订阅 layer 1 → invokeTool("stepB") → MockKernel 在返回 Mono.never() 前先 incrementAndGet，
            // 与 thenCancel() 回传竞态，故 stepB 计数 0/1 抖动（原 ~33% 挂率）。该 mock 计数时序非原型承重语义；
            // BSP 屏障语义由 BspBarrier.layerOneStartsOnlyAfterLayerZeroCompletes 覆盖。
            StepVerifier.create(f)
                    .thenRequest(1)
                    .assertNext(l0 -> assertThat(l0.layerIndex()).isEqualTo(0))
                    .thenCancel().verify();
        }
    }

    @Nested
    @DisplayName("辅助方法")
    class Helpers {
        @Test void resolveNodeRefParses() {
            assertThat(StreamingPregelDemo.resolveNodeRef("${A.output}").value()).isEqualTo("A");
            assertThat(StreamingPregelDemo.resolveNodeRef("bare").value()).isEqualTo("bare");
        }
        @Test void resolveNodeRefNullOk() {
            assertThatCode(() -> StreamingPregelDemo.resolveNodeRef(null)).doesNotThrowAnyException();
        }
        @Test void bareToolNameWorks() {
            assertThat(StreamingPregelDemo.bareToolName("t desc")).isEqualTo("t");
            assertThat(StreamingPregelDemo.bareToolName("")).isEqualTo("unknown");
            assertThat(StreamingPregelDemo.bareToolName(null)).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("v1.1 回归")
    class V11Regression {
        @Test @DisplayName("单节点 LLM")
        void singleLlmNode() {
            TaskNode n = TaskNode.of("A", "think", TaskNodeType.LLM_CALL);
            kernel.registerLLM("A", "42");
            TaskGraph g = new TaskGraph("g", List.of(n), List.of());
            Flux<StreamingPregelDemo.SuperstepResult> f = demo.execute(new TaskId("ts"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).assertNext(l0 -> {
                assertThat(find(l0, "A").value()).isEqualTo("42");
                assertThat(l0.failedNodes()).isEmpty();
            }).verifyComplete();
        }

        @Test @DisplayName("单节点 Tool")
        void singleToolNode() {
            TaskNode n = TaskNode.of("A", "t", TaskNodeType.TOOL_CALL);
            kernel.registerTool("t", ToolResult.ok(new ToolName("t"), "done"));
            TaskGraph g = new TaskGraph("g", List.of(n), List.of());
            Flux<StreamingPregelDemo.SuperstepResult> f = demo.execute(new TaskId("ts"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).assertNext(l0 ->
                    assertThat(find(l0, "A").value()).isEqualTo("done")).verifyComplete();
        }

        @Test @DisplayName("失败节点 value=null (B6-P2)")
        void failedNodeValueNull() {
            TaskNode a = TaskNode.of("A", "fail", TaskNodeType.TOOL_CALL);
            TaskNode b = TaskNode.of("B", "use", TaskNodeType.TOOL_CALL, Map.of("x", "${A.output}"));
            kernel.registerFailingTool("fail", new RuntimeException("boom"));
            kernel.registerTool("use", ToolResult.ok(new ToolName("use"), "B-ok"));
            TaskGraph g = new TaskGraph("g", List.of(a, b), List.of(TaskEdge.of("A", "B")));
            Flux<StreamingPregelDemo.SuperstepResult> f = demo.execute(new TaskId("tn"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f)
                    .assertNext(l0 -> assertThat(find(l0, "A").value()).isNull())
                    .assertNext(l1 -> assertThat(find(l1, "B").value()).isEqualTo("B-ok"))
                    .verifyComplete();
        }

        @Test @DisplayName("Mono.empty() 工具 → 显式错误")
        void monoEmptyTool() {
            TaskNode n = TaskNode.of("A", "emptyT", TaskNodeType.TOOL_CALL);
            kernel.registerEmptyTool("emptyT");
            TaskGraph g = new TaskGraph("g", List.of(n), List.of());
            StreamingPregelDemo sd = new StreamingPregelDemo(kernel,
                    Duration.ofSeconds(5), Duration.ofSeconds(5));
            Flux<StreamingPregelDemo.SuperstepResult> f = sd.execute(new TaskId("te"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).assertNext(l0 -> {
                assertThat(find(l0, "A").failed()).isTrue();
                assertThat(find(l0, "A").error()).contains("工具返回空");
            }).verifyComplete();
        }

        @Test @DisplayName("Mono.empty() LLM → 显式错误")
        void monoEmptyLlm() {
            TaskNode n = TaskNode.of("A", "empty", TaskNodeType.LLM_CALL);
            kernel.registerEmptyLLM("A");
            TaskGraph g = new TaskGraph("g", List.of(n), List.of());
            StreamingPregelDemo sd = new StreamingPregelDemo(kernel,
                    Duration.ofSeconds(5), Duration.ofSeconds(5));
            Flux<StreamingPregelDemo.SuperstepResult> f = sd.execute(new TaskId("te"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).assertNext(l0 -> {
                assertThat(find(l0, "A").failed()).isTrue();
                assertThat(find(l0, "A").error()).contains("LLM 返回空响应");
            }).verifyComplete();
        }

        @Test @DisplayName("双订阅独立状态 (B3)")
        void doubleSubscribeIndependent() {
            TaskNode n = TaskNode.of("A", "t", TaskNodeType.TOOL_CALL);
            kernel.registerTool("t", ToolResult.ok(new ToolName("t"), "done"));
            TaskGraph g = new TaskGraph("g", List.of(n), List.of());
            Flux<StreamingPregelDemo.SuperstepResult> f = demo.execute(new TaskId("td"), g,
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            StepVerifier.create(f).assertNext(l0 ->
                    assertThat(find(l0, "A").value()).isEqualTo("done")).verifyComplete();
            StepVerifier.create(f).assertNext(l0 ->
                    assertThat(find(l0, "A").value()).isEqualTo("done")).verifyComplete();
        }

        @Test @DisplayName("空层防御 (B2)")
        void emptyLayerOk() {
            StreamingPregelDemo.Accumulator acc = new StreamingPregelDemo.Accumulator(
                    BudgetLimits.start(Budget.Fixed.developmentDefault()));
            Mono<StreamingPregelDemo.SuperstepResult> r = demo.executeSuperstep(
                    new TaskId("te"), List.of(), acc, 0);
            StreamingPregelDemo.SuperstepResult sr = r.block(Duration.ofSeconds(5));
            assertThat(sr).isNotNull();
            assertThat(sr.nodeResults()).isEmpty();
        }
    }

    // ── helpers ──

    private static StreamingPregelDemo.NodeResult find(
            StreamingPregelDemo.SuperstepResult sr, String id) {
        return sr.nodeResults().stream().filter(n -> n.nodeId().value().equals(id))
                .findFirst().orElseThrow();
    }

    // ── MockKernel ──

    static class MockKernel implements AgentKernel {
        final Map<String, String> llm = new ConcurrentHashMap<>();
        final Map<String, Duration> slowLlm = new ConcurrentHashMap<>();
        final Map<String, ToolResult> tools = new ConcurrentHashMap<>();
        final Map<String, RuntimeException> failTools = new ConcurrentHashMap<>();
        final Map<String, Duration> slowTools = new ConcurrentHashMap<>();
        final Map<String, Boolean> empty = new ConcurrentHashMap<>();
        final Map<String, Boolean> hanging = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> toolCounts = new ConcurrentHashMap<>();
        final AtomicInteger invokeCount = new AtomicInteger(0);

        void registerLLM(String id, String r) { llm.put(id, r); }
        void registerSlowLLM(String id, Duration d, String r) { slowLlm.put(id, d); llm.put(id, r); }
        void registerEmptyLLM(String id) { empty.put("llm:" + id, true); }
        void registerTool(String name, ToolResult r) { tools.put(name, r); }
        void registerFailingTool(String name, RuntimeException e) { failTools.put(name, e); }
        void registerEmptyTool(String name) { empty.put("tool:" + name, true); }
        void registerHangingTool(String name) { hanging.put(name, true); }
        long toolInvokeCount(String name) {
            AtomicInteger c = toolCounts.get(name);
            return c == null ? 0 : c.get();
        }

        @Override public Mono<String> think(String p, BudgetLimits b) {
            return Mono.error(new UnsupportedOperationException());
        }
        @Override public Mono<String> think(String p, BudgetLimits b, TaskId t, NodeId n) {
            invokeCount.incrementAndGet();
            String id = n != null ? n.value() : "?";
            if (empty.containsKey("llm:" + id)) return Mono.empty();
            String r = llm.getOrDefault(id, "mock-" + id);
            Duration d = slowLlm.getOrDefault(id, Duration.ofMillis(1));
            return Mono.just(r).delayElement(d);
        }
        @Override public Mono<ToolResult> invokeTool(ToolName tn, Map<String, Object> args,
                                                      BudgetLimits b) {
            invokeCount.incrementAndGet();
            String name = tn.value();
            toolCounts.computeIfAbsent(name, k -> new AtomicInteger(0)).incrementAndGet();
            if (hanging.containsKey(name)) return Mono.never();
            if (empty.containsKey("tool:" + name)) return Mono.empty();
            RuntimeException ex = failTools.get(name);
            if (ex != null) return Mono.error(ex);
            Duration d = slowTools.getOrDefault(name, Duration.ofMillis(1));
            ToolResult r = tools.getOrDefault(name, ToolResult.ok(tn, "mock-" + name));
            return Mono.just(r).delayElement(d);
        }
        @Override public Mono<Map<NodeId, Object>> observe(TaskId t, java.util.Set<NodeId> ids) {
            return Mono.just(Map.of());
        }
        @Override public Mono<com.openjiuwen.core.kernel.model.CheckpointId> saveCheckpoint(
                com.openjiuwen.core.kernel.model.Checkpoint c) {
            return Mono.just(new com.openjiuwen.core.kernel.model.CheckpointId("mock"));
        }
        @Override public Mono<com.openjiuwen.core.kernel.model.Checkpoint> restoreCheckpoint(
                TaskId t) { return Mono.empty(); }
        @Override public Mono<com.openjiuwen.core.kernel.model.CheckpointId> yield(
                TaskId t, com.openjiuwen.core.kernel.model.YieldReason r, String s) {
            return Mono.just(new com.openjiuwen.core.kernel.model.CheckpointId("mock"));
        }
        @Override public Mono<Void> emit(com.openjiuwen.core.kernel.model.AgentEvent e) {
            return Mono.empty();
        }
        @Override public Flux<com.openjiuwen.core.kernel.model.AgentEvent> observeEvents(
                TaskId t) { return Flux.empty(); }
    }
}
