package com.openjiuwen.runtime.alpha.verifier;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.alpha.graph.*;
import com.openjiuwen.core.alpha.model.*;
import com.openjiuwen.core.alpha.verifier.NodeResult;
import com.openjiuwen.core.alpha.verifier.VerifyResult;
import com.openjiuwen.core.kernel.model.*;
import com.openjiuwen.core.meta.AgentDefinition;
import com.openjiuwen.runtime.core.engine.AgentKernel;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * DefaultVerifier.verify() mock 控制流承重——证 strictVerify/lightVerify/ruleVerify
 * 所有关键分支（含 NodeResult sealed type 检测、rule→LLM 短路、expectedOutput 边界）。
 *
 * <p>每个测试标注 mutation-RED：剥什么断言会红。
 *
 * <p>承重契约：本类证 verify() 控制流（硬断言），真 LLM 数据通道软观察 defer。
 * 与 {@link DefaultVerifierTest}（decideReplanStrategy 纯函数）正交互补。
 */
@DisplayName("DefaultVerifier.verify() mock 控制流承重（8 场景）")
class DefaultVerifierVerifyTest {

    // ==================== mock kernel ====================

    /** Kernel that returns pre-configured think responses. */
    static class ThinkKernel implements AgentKernel {
        final List<String> prompts = new ArrayList<>();
        final List<String> responses;
        final AtomicInteger callIdx = new AtomicInteger(0);

        ThinkKernel(String... responses) {
            this.responses = new ArrayList<>(List.of(responses));
        }

        @Override
        public Mono<String> think(String prompt, BudgetLimits budget) {
            prompts.add(prompt);
            int i = Math.min(callIdx.getAndIncrement(), responses.size() - 1);
            return Mono.just(responses.get(i));
        }

        @Override
        public Mono<ToolResult> invokeTool(ToolName toolName, Map<String, Object> arguments,
                                            BudgetLimits budget) {
            return Mono.just(ToolResult.ok(toolName, "mock"));
        }

        @Override public Mono<Void> emit(AgentEvent event) { return Mono.empty(); }
        @Override public Mono<Map<NodeId, Object>> observe(TaskId taskId, Set<NodeId> nodeIds) {
            return Mono.just(Map.of());
        }
        @Override public Mono<CheckpointId> saveCheckpoint(Checkpoint cp) {
            return Mono.just(new CheckpointId("cp"));
        }
        @Override public Mono<Checkpoint> restoreCheckpoint(TaskId taskId) { return Mono.empty(); }
        @Override public Mono<CheckpointId> yield(TaskId taskId, YieldReason reason, String state) {
            return Mono.just(new CheckpointId("y"));
        }
        @Override public Flux<AgentEvent> observeEvents(TaskId taskId) { return Flux.empty(); }
    }

    // ==================== helpers ====================

    private static TaskGraph graphOf(TaskNode... nodes) {
        return new TaskGraph("goal", List.of(nodes), List.of());
    }

    private static TaskNode llmNode(String id, String desc) {
        return TaskNode.of(id, desc, TaskNodeType.LLM_CALL);
    }

    private static ExecutionPolicy strictPolicy() {
        return new ExecutionPolicy(PlanningMode.AUTO, VerifyMode.STRICT, 3, 4, true);
    }

    private static BudgetLimits budget() {
        return BudgetLimits.start(Budget.Fixed.productionDefault());
    }

    // ==================== V1: null result → ruleVerify catches ====================

    @Test
    @DisplayName("V1: null 输出→ruleVerify 检出→全部失败→短路跳 LLM")
    void nullResultCaughtByRuleVerify() {
        ThinkKernel kernel = new ThinkKernel("should-not-be-called");
        DefaultVerifier verifier = new DefaultVerifier(kernel);
        TaskGraph graph = graphOf(llmNode("A", "analyze"));
        Map<NodeId, Object> results = Map.of(); // node A has no result → null

        VerifyResult vr = verifier.verify(new TaskId("t1"),
                PlanGoal.of("test", List.of(), Set.of(), Map.of()),
                graph, results, strictPolicy(), budget()).block();

        assertThat(vr).isNotNull();
        assertThat(vr.passed()).isFalse();
        assertThat(vr.failedNodes()).contains("A");
        // mutation-RED: 剥 null check → node A not in failedNodes → RED
    }

    // ==================== V2: DeviceFailure → ruleVerify catches ====================

    @Test
    @DisplayName("V2: NodeResult.DeviceFailure→ruleVerify 检出（AAC sealed type 检测）")
    void deviceFailureCaughtByRuleVerify() {
        ThinkKernel kernel = new ThinkKernel("should-not-be-called");
        DefaultVerifier verifier = new DefaultVerifier(kernel);
        TaskGraph graph = graphOf(llmNode("A", "call-tool"));
        Map<NodeId, Object> results = Map.of(
                new NodeId("A"), new NodeResult.DeviceFailure("A", "连接超时", true));

        VerifyResult vr = verifier.verify(new TaskId("t1"),
                PlanGoal.of("test", List.of(), Set.of(), Map.of()),
                graph, results, strictPolicy(), budget()).block();

        assertThat(vr).isNotNull();
        assertThat(vr.passed()).isFalse();
        assertThat(vr.failedNodes()).contains("A");
        // mutation-RED: 剥 DeviceFailure instanceof check → node A passed → RED
    }

    // ==================== V3: VerifierFailure → ruleVerify catches ====================

    @Test
    @DisplayName("V3: NodeResult.VerifierFailure→ruleVerify 检出")
    void verifierFailureCaughtByRuleVerify() {
        ThinkKernel kernel = new ThinkKernel("should-not-be-called");
        DefaultVerifier verifier = new DefaultVerifier(kernel);
        TaskGraph graph = graphOf(llmNode("A", "analyze"));
        Map<NodeId, Object> results = Map.of(
                new NodeId("A"), new NodeResult.VerifierFailure("A", "输出与期望不符"));

        VerifyResult vr = verifier.verify(new TaskId("t1"),
                PlanGoal.of("test", List.of(), Set.of(), Map.of()),
                graph, results, strictPolicy(), budget()).block();

        assertThat(vr).isNotNull();
        assertThat(vr.passed()).isFalse();
        assertThat(vr.failedNodes()).contains("A");
        // mutation-RED: 剥 VerifierFailure instanceof check → node A passed → RED
    }

    // ==================== V4: FAILED_PREFIX string → backward compat ====================

    @Test
    @DisplayName("V4: FAILED: 前缀字符串→向后兼容 1.0 ReActAgent 协议")
    void failedPrefixStringStillDetected() {
        ThinkKernel kernel = new ThinkKernel("should-not-be-called");
        DefaultVerifier verifier = new DefaultVerifier(kernel);
        TaskGraph graph = graphOf(llmNode("A", "call-tool"));
        Map<NodeId, Object> results = Map.of(
                new NodeId("A"), "FAILED: 工具调用超时");

        VerifyResult vr = verifier.verify(new TaskId("t1"),
                PlanGoal.of("test", List.of(), Set.of(), Map.of()),
                graph, results, strictPolicy(), budget()).block();

        assertThat(vr).isNotNull();
        assertThat(vr.passed()).isFalse();
        assertThat(vr.failedNodes()).contains("A");
        // mutation-RED: 剥 FAILED_PREFIX check → node A passed → RED
    }

    // ==================== V5: expectedOutput mismatch → ruleVerify catches ====================

    @Test
    @DisplayName("V5: expectedOutput 不匹配→ruleVerify 检出失败")
    void expectedOutputMismatchCaught() {
        ThinkKernel kernel = new ThinkKernel("should-not-be-called");
        DefaultVerifier verifier = new DefaultVerifier(kernel);
        TaskNode node = new TaskNode(new NodeId("B"), "summarize", TaskNodeType.LLM_CALL,
                Map.of(), "REQUIRED_KEYWORD", null, null);
        TaskGraph graph = graphOf(node);
        Map<NodeId, Object> results = Map.of(new NodeId("B"), "some irrelevant output");

        VerifyResult vr = verifier.verify(new TaskId("t1"),
                PlanGoal.of("test", List.of(), Set.of(), Map.of()),
                graph, results, strictPolicy(), budget()).block();

        assertThat(vr).isNotNull();
        assertThat(vr.passed()).isFalse();
        assertThat(vr.failedNodes()).contains("B");
        // mutation-RED: 剥 expectedOutput check → node B passed → RED
    }

    // ==================== V6: rule pass → LLM verify → PASS ====================

    @Test
    @DisplayName("V6: ruleVerify 通过→LLM verify 判断 PASS→最终 passed=true")
    void rulePassThenLlmVerifyPasses() {
        // LLM response: "A: PASS"
        ThinkKernel kernel = new ThinkKernel("A: PASS");
        DefaultVerifier verifier = new DefaultVerifier(kernel);
        TaskGraph graph = graphOf(llmNode("A", "analyze data"));
        Map<NodeId, Object> results = Map.of(new NodeId("A"), "analysis result here");

        VerifyResult vr = verifier.verify(new TaskId("t1"),
                PlanGoal.of("test", List.of(), Set.of(), Map.of()),
                graph, results, strictPolicy(), budget()).block();

        assertThat(vr).isNotNull();
        assertThat(vr.passed()).isTrue();
        assertThat(kernel.prompts).isNotEmpty(); // LLM was called
        // mutation-RED: 剥 LLM verify → kernel.prompts empty → RED
    }

    // ==================== V7: lightVerify → PASS ====================

    @Test
    @DisplayName("V7: LIGHT 模式→LLM 返回 PASS→最终 passed=true")
    void lightVerifyPasses() {
        ThinkKernel kernel = new ThinkKernel("PASS: 结果完整回答了目标");
        DefaultVerifier verifier = new DefaultVerifier(kernel);
        TaskGraph graph = graphOf(llmNode("A", "analyze"));
        Map<NodeId, Object> results = Map.of(new NodeId("A"), "result");
        ExecutionPolicy lightPolicy = new ExecutionPolicy(
                PlanningMode.AUTO, VerifyMode.LIGHT, 3, 4, true);

        VerifyResult vr = verifier.verify(new TaskId("t1"),
                PlanGoal.of("test", List.of(), Set.of(), Map.of()),
                graph, results, lightPolicy, budget()).block();

        assertThat(vr).isNotNull();
        assertThat(vr.passed()).isTrue();
        assertThat(kernel.prompts).isNotEmpty();
        // mutation-RED: 剥 PASS 检测 → vr.passed() false → RED
    }

    // ==================== V8: VerifyMode.NONE → skip ====================

    @Test
    @DisplayName("V8: VerifyMode.NONE→跳过验证→passed=true")
    void noneModeSkipsVerification() {
        ThinkKernel kernel = new ThinkKernel("should-not-be-called");
        DefaultVerifier verifier = new DefaultVerifier(kernel);
        TaskGraph graph = graphOf(llmNode("A", "analyze"));
        Map<NodeId, Object> results = Map.of(new NodeId("A"), "result");
        ExecutionPolicy nonePolicy = new ExecutionPolicy(
                PlanningMode.AUTO, VerifyMode.NONE, 3, 4, true);

        VerifyResult vr = verifier.verify(new TaskId("t1"),
                PlanGoal.of("test", List.of(), Set.of(), Map.of()),
                graph, results, nonePolicy, budget()).block();

        assertThat(vr).isNotNull();
        assertThat(vr.passed()).isTrue();
        assertThat(vr.overallFeedback()).contains("跳过");
        assertThat(kernel.prompts).isEmpty(); // no LLM call
        // mutation-RED: 剥 NONE 短路 → kernel.prompts 非空 → RED
    }
}
