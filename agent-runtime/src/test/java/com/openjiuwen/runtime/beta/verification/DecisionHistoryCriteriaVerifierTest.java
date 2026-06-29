package com.openjiuwen.runtime.beta.verification;

import com.openjiuwen.core.beta.model.GoalSpec;
import com.openjiuwen.core.beta.model.LLMDecision;
import com.openjiuwen.core.kernel.model.AgentEvent;
import com.openjiuwen.core.kernel.model.Budget;
import com.openjiuwen.core.kernel.model.BudgetLimits;
import com.openjiuwen.core.kernel.model.Checkpoint;
import com.openjiuwen.core.kernel.model.CheckpointId;
import com.openjiuwen.core.kernel.model.NodeId;
import com.openjiuwen.core.kernel.model.TaskId;
import com.openjiuwen.core.kernel.model.ToolName;
import com.openjiuwen.core.kernel.model.ToolResult;
import com.openjiuwen.core.kernel.model.Violation;
import com.openjiuwen.core.kernel.model.YieldReason;
import com.openjiuwen.runtime.core.engine.AgentKernel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DecisionHistoryCriteriaVerifier 承重测试——criteria 验证闭环的核心承重契约。
 *
 * <p>承重三层（mock 证控制流，真 LLM judge 软观察 defer 到轮9）：
 * <ul>
 *   <li>规则短路：输出全覆盖 / 历史 50% 覆盖 / 两者明确 FAIL → 均 think 零调用（IFF：剥 think 计数→RED）</li>
 *   <li>budget 门双向（铁律⑰ config-consumer-reachability）：setBudgetLimits + UNDETERMINED → think 被调；
 *       不 setBudgetLimits + 同输入 → think 零调用 + 降级 fail。双向证开关活、非死配置。</li>
 *   <li>judge IFF：think 返回 verdict PASS/FAIL → passed 翻转（剥 token→RED）。</li>
 *   <li>parseJudgeVerdict 防误判："DO NOT PASS" 不应判通过（避免 contains("PASS") 弱匹配）。</li>
 * </ul>
 *
 * <p>承重断言真化（gate 铁律③）：用 stub.thinkPrompts.size() 计数断言（剥 token→RED IFF），
 * 非"有调用/非空"弱断言。mutation-RED 验证点见各 @Nested javadoc。
 */
@DisplayName("DecisionHistoryCriteriaVerifier: criteria 验证承重")
class DecisionHistoryCriteriaVerifierTest {

    private StubKernel stub;

    @BeforeEach
    void setUp() {
        stub = new StubKernel();
        stub.thinkResponse = "{\"verdict\":\"PASS\"}";
    }

    private static GoalSpec goal(String... criteria) {
        return GoalSpec.of("test-goal", List.of(criteria));
    }

    private static LLMDecision.Complete complete(String output) {
        return new LLMDecision.Complete(output, 0.9, "summary");
    }

    // ==================== 规则短路（think 零调用） ====================

    @Nested
    @DisplayName("规则判断短路：不降级 LLM（mutation-RED: 删 checkOutputCoverage PASS 提前返回 → 零调用断言红）")
    class RuleShortCircuit {

        @Test
        @DisplayName("无 successCriteria → 直接通过，think 零调用")
        void emptyCriteriaPassesWithoutLlm() {
            DecisionHistoryCriteriaVerifier verifier = new DecisionHistoryCriteriaVerifier(stub);
            verifier.setBudgetLimits(BudgetLimits.start(Budget.Fixed.productionDefault()));

            List<Violation> violations = verifier.verify(goal(), List.of(), complete("anything"));

            assertThat(violations).isEmpty();
            assertThat(stub.thinkPrompts).isEmpty();
        }

        @Test
        @DisplayName("输出全覆盖 → 规则 PASS，think 零调用")
        void outputFullCoveragePassesWithoutLlm() {
            DecisionHistoryCriteriaVerifier verifier = new DecisionHistoryCriteriaVerifier(stub);
            verifier.setBudgetLimits(BudgetLimits.start(Budget.Fixed.productionDefault()));

            // criterion 关键词 [apple, banana] 全在 output → outputCheck PASS
            List<Violation> violations = verifier.verify(
                    goal("apple banana"), List.of(), complete("the apple and banana are fresh"));

            assertThat(violations).isEmpty();
            assertThat(stub.thinkPrompts).isEmpty();
        }

        @Test
        @DisplayName("历史 50% 覆盖 → 规则 PASS，think 零调用")
        void historyCoveragePassesWithoutLlm() {
            DecisionHistoryCriteriaVerifier verifier = new DecisionHistoryCriteriaVerifier(stub);
            verifier.setBudgetLimits(BudgetLimits.start(Budget.Fixed.productionDefault()));

            // output 不含关键词（outputCheck FAIL），但 history 的 CallTool reasoning 覆盖 ≥50% → historyCheck PASS
            List<LLMDecision> history = List.of(new LLMDecision.CallTool(
                    new ToolName("fetch"), Map.of(), "fetch apple and banana"));
            List<Violation> violations = verifier.verify(
                    goal("apple banana"), history, complete("totally unrelated output"));

            assertThat(violations).isEmpty();
            assertThat(stub.thinkPrompts).isEmpty();
        }

        @Test
        @DisplayName("输出和历史均明确 FAIL → FAIL 不降级 LLM，think 零调用")
        void bothFailYieldsViolationWithoutLlm() {
            DecisionHistoryCriteriaVerifier verifier = new DecisionHistoryCriteriaVerifier(stub);
            verifier.setBudgetLimits(BudgetLimits.start(Budget.Fixed.productionDefault()));

            // output 零匹配（FAIL），history 空零匹配（FAIL）→ 明确 FAIL，不进 fallback
            List<Violation> violations = verifier.verify(
                    goal("apple banana"), List.of(), complete("xyz nothing here"));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).isInstanceOf(Violation.CriteriaNotCovered.class);
            assertThat(stub.thinkPrompts).isEmpty();
        }
    }

    // ==================== budget 门双向 IFF（铁律⑰） ====================

    @Nested
    @DisplayName("budget 门双向 IFF（铁律⑰）")
    class BudgetGate {

        // 触发 fallback（LLM_JUDGE）的输入：output 部分匹配 → outputCheck UNDETERMINED；history 空无匹配 → historyCheck FAIL
        private static final String UNDETERMINED_CRITERION = "apple banana cherry";
        private static final String PARTIAL_OUTPUT = "apple banana only";

        @Test
        @DisplayName("setBudgetLimits + UNDETERMINED → think 被调（IFF：thinkPrompts 非空）")
        void budgetSet_triggersLlmJudge() {
            DecisionHistoryCriteriaVerifier verifier = new DecisionHistoryCriteriaVerifier(stub);
            verifier.setBudgetLimits(BudgetLimits.start(Budget.Fixed.productionDefault()));

            verifier.verify(goal(UNDETERMINED_CRITERION), List.of(), complete(PARTIAL_OUTPUT));

            // judge 通道活：think 被调用且收到含 criterion 的 prompt
            assertThat(stub.thinkPrompts)
                    .as("setBudgetLimits 后 UNDETERMINED 应触发 LLM judge")
                    .hasSize(1);
            assertThat(stub.thinkPrompts.get(0)).contains(UNDETERMINED_CRITERION);
        }

        @Test
        @DisplayName("不 setBudgetLimits + 同输入 → think 零调用 + 降级 fail（IFF：双向证开关活）")
        void budgetNotSet_degradesFailWithoutLlm() {
            DecisionHistoryCriteriaVerifier verifier = new DecisionHistoryCriteriaVerifier(stub);
            // 故意不调 setBudgetLimits（budgetLimits == null）

            List<Violation> violations = verifier.verify(
                    goal(UNDETERMINED_CRITERION), List.of(), complete(PARTIAL_OUTPUT));

            // budget 门生效：kernel 未注入 budget → 零 LLM 调用 + 降级 fail
            assertThat(stub.thinkPrompts)
                    .as("不 setBudgetLimits 时 budget 门应阻止 LLM 调用（防 stale budget）")
                    .isEmpty();
            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).isInstanceOf(Violation.CriteriaNotCovered.class);
            // mutation-RED 证承重非恒真：若删 budget 门（kernel==null||budgetLimits==null 检查），
            // 则不 setBudgetLimits 时仍会调 think → 此处 thinkPrompts.isEmpty() 断言翻转为 RED
        }

        @Test
        @DisplayName("无 kernel 的无参构造器 → ASSUME_FAIL，think 零调用")
        void noKernelFallbackAssumeFail() {
            DecisionHistoryCriteriaVerifier verifier = new DecisionHistoryCriteriaVerifier();
            verifier.setBudgetLimits(BudgetLimits.start(Budget.Fixed.productionDefault()));

            List<Violation> violations = verifier.verify(
                    goal(UNDETERMINED_CRITERION), List.of(), complete(PARTIAL_OUTPUT));

            // 无 kernel → ASSUME_FAIL 策略，不调 LLM
            assertThat(stub.thinkPrompts).isEmpty();
            assertThat(violations).hasSize(1);
        }
    }

    // ==================== judge IFF + parseJudgeVerdict 防误判 ====================

    @Nested
    @DisplayName("judge IFF + parseJudgeVerdict 防误判")
    class JudgeVerdict {

        private static final String UNDETERMINED_CRITERION = "apple banana cherry";
        private static final String PARTIAL_OUTPUT = "apple banana only";

        private List<Violation> verifyWithJudgeResponse(String judgeResponse) {
            stub.thinkResponse = judgeResponse;
            DecisionHistoryCriteriaVerifier verifier = new DecisionHistoryCriteriaVerifier(stub);
            verifier.setBudgetLimits(BudgetLimits.start(Budget.Fixed.productionDefault()));
            return verifier.verify(goal(UNDETERMINED_CRITERION), List.of(), complete(PARTIAL_OUTPUT));
        }

        @Test
        @DisplayName("judge verdict PASS → 该 criterion 通过（IFF：剥 token PASS→FAIL 翻转为 violation）")
        void judgePassYieldsNoViolation() {
            List<Violation> violations = verifyWithJudgeResponse("{\"verdict\":\"PASS\"}");

            assertThat(stub.thinkPrompts).hasSize(1);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("judge verdict FAIL → 该 criterion 未通过")
        void judgeFailYieldsViolation() {
            List<Violation> violations = verifyWithJudgeResponse("{\"verdict\":\"FAIL\"}");

            assertThat(stub.thinkPrompts).hasSize(1);
            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).isInstanceOf(Violation.CriteriaNotCovered.class);
        }

        @Test
        @DisplayName("\"DO NOT PASS\" 不应误判为通过（防 contains(\"PASS\") 弱匹配）")
        void judgeRejectsDoNotPass() {
            // 非合法 JSON（含 DO NOT PASS 文本）→ JSON 解析失败 → 独立行回退 → 无独立 "PASS" 行 → false
            List<Violation> violations = verifyWithJudgeResponse("DO NOT PASS this criterion");

            assertThat(stub.thinkPrompts).hasSize(1);
            assertThat(violations)
                    .as("\"DO NOT PASS\" 不含独立行 PASS，应判未通过")
                    .hasSize(1);
        }

        @Test
        @DisplayName("judge 独立行 PASS 回退生效")
        void judgeStandalonePassFallback() {
            // 非 JSON，但含独立 "PASS" 行 → 回退判定通过
            List<Violation> violations = verifyWithJudgeResponse("分析完毕\nPASS");

            assertThat(violations).isEmpty();
        }
    }

    // ==================== Violation 字段承重 ====================

    @Test
    @DisplayName("未通过 criterion 产出 CriteriaNotCovered，含 criterion + reason 字段")
    void unmetCriterionProducesCriteriaNotCoveredViolation() {
        DecisionHistoryCriteriaVerifier verifier = new DecisionHistoryCriteriaVerifier(stub);
        verifier.setBudgetLimits(BudgetLimits.start(Budget.Fixed.productionDefault()));

        List<Violation> violations = verifier.verify(
                goal("apple banana"), List.of(), complete("nothing matches"));

        assertThat(violations).hasSize(1);
        Violation.CriteriaNotCovered v = (Violation.CriteriaNotCovered) violations.get(0);
        assertThat(v.criterion()).isEqualTo("apple banana");
        assertThat(v.reason()).contains("apple banana");
        assertThat(v.code()).isEqualTo("CRITERIA_NOT_COVERED");
        assertThat(v.severity()).isEqualTo(Violation.Severity.HIGH);
    }

    /** 最小 AgentKernel stub：只真实现 think（记录 prompt + budget，可控响应），其余抛 UnsupportedOperationException。 */
    static final class StubKernel implements AgentKernel {
        final List<String> thinkPrompts = new ArrayList<>();
        final List<BudgetLimits> thinkBudgets = new ArrayList<>();
        volatile String thinkResponse = "{\"verdict\":\"PASS\"}";

        @Override
        public Mono<String> think(String prompt, BudgetLimits budget) {
            thinkPrompts.add(prompt);
            thinkBudgets.add(budget);
            return Mono.just(thinkResponse);
        }

        @Override
        public Mono<ToolResult> invokeTool(ToolName toolName, Map<String, Object> arguments, BudgetLimits budget) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Map<NodeId, Object>> observe(TaskId taskId, Set<NodeId> nodeIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CheckpointId> saveCheckpoint(Checkpoint checkpoint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Checkpoint> restoreCheckpoint(TaskId taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CheckpointId> yield(TaskId taskId, YieldReason reason, String currentState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Void> emit(AgentEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<AgentEvent> observeEvents(TaskId taskId) {
            throw new UnsupportedOperationException();
        }
    }
}
