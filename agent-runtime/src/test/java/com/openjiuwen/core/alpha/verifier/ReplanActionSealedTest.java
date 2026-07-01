package com.openjiuwen.core.alpha.verifier;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.runtime.beta.selfheal.RootCauseDiagnoser;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ReplanAction sealed type 承重测试——AAC dispatch 纯函数 + sealed 穷举 3 态 + 数据携带 IFF。
 *
 * <p>扁平 @Test（非 @Nested）——匹配项目 convention。
 *
 * <p>承重 IFF（AAC 核心 dispatch ~20 行）：
 * <ul>
 *   <li>sealed permits 3 态——删任一 → 编译红（类型层 mutation-prove）。</li>
 *   <li>DeviceFailure → AcceptPartial（永不应重试，剥此映射 → RED——设备故障 replan 浪费轮次）。</li>
 *   <li>PerceptionUnreliable → AcceptPartial（永不应重试，剥此映射 → RED——verifier 不可信盲信 FAILED）。</li>
 *   <li>PlanOrAnswerError 少量节点（≤2）→ LocalReplan（剥此阈值 → RED——浪费全局 replan 成本）。</li>
 *   <li>PlanOrAnswerError 大量节点（>2）→ GlobalReplan（剥此阈值 → RED——局部不够覆盖）。</li>
 *   <li>PlanOrAnswerError 空失败节点集 → GlobalReplan（P4 Bug #9，剥此 → RED——空集 LocalReplan 无意义）。</li>
 * </ul>
 */
class ReplanActionSealedTest {

    // ==================== Sealed exhaustiveness ====================

    @Test
    void sealedPermits3States() {
        Class<?>[] permitted = ReplanAction.class.getPermittedSubclasses();
        assertThat(permitted).hasSize(3);
        assertThat(permitted).extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder("LocalReplan", "GlobalReplan", "AcceptPartial");
    }

    // ==================== Data carrying ====================

    @Test
    void localReplanCarriesFailedNodesAndFeedback() {
        // mutation-RED：剥 failedNodes → RED（不知道重执行哪些节点）
        ReplanAction.LocalReplan lr = new ReplanAction.LocalReplan(
                Set.of("n1", "n2"), "节点 n1 输出格式错误，请用 JSON");
        assertThat(lr.failedNodes()).containsExactlyInAnyOrder("n1", "n2");
        assertThat(lr.feedback()).isEqualTo("节点 n1 输出格式错误，请用 JSON");
    }

    @Test
    void globalReplanCarriesFeedback() {
        // mutation-RED：剥 feedback → RED（新 plan 无上下文提示）
        ReplanAction.GlobalReplan gr = new ReplanAction.GlobalReplan("原 plan 全部节点失败，重新规划");
        assertThat(gr.feedback()).isEqualTo("原 plan 全部节点失败，重新规划");
    }

    @Test
    void acceptPartialCarriesReason() {
        // mutation-RED：剥 reason → RED（降级原因丢失，无可审计性）
        ReplanAction.AcceptPartial ap = new ReplanAction.AcceptPartial("Device failure: [n1]");
        assertThat(ap.reason()).contains("Device failure");
        assertThat(ap.reason()).contains("n1");
    }

    // ==================== AAC dispatch: RootCause → ReplanAction ====================
    // 这些是 AAC 的核心 ~20 行 dispatch 纯函数测试。每个测试标注 mutation-RED：
    // 在 toReplanAction() 中改对应的 case arm 返回 → 该测试变 RED。

    @Test
    void dispatchDeviceFailureReturnsAcceptPartial() {
        // 承重 IFF：DeviceFailure 永不应 retry
        // mutation-RED：改 toReplanAction DeviceFailure arm 返回 LocalReplan → RED
        RootCause cause = new RootCause.DeviceFailure(Set.of("tool1"));
        ReplanAction action = RootCauseDiagnoser.toReplanAction(cause, "verify says n1 failed", Set.of("n1"));

        assertThat(action).isInstanceOf(ReplanAction.AcceptPartial.class);
        ReplanAction.AcceptPartial ap = (ReplanAction.AcceptPartial) action;
        assertThat(ap.reason()).contains("Device failure");
        assertThat(ap.reason()).contains("tool1");
        assertThat(ap.reason()).contains("replan cannot fix");
    }

    @Test
    void dispatchPerceptionUnreliableReturnsAcceptPartial() {
        // 承重 IFF：PerceptionUnreliable 永不应 retry——verifier 不可信，其 FAILED 也不可信
        // mutation-RED：改 toReplanAction PerceptionUnreliable arm 返回 LocalReplan → RED
        RootCause cause = new RootCause.PerceptionUnreliable(true);
        ReplanAction action = RootCauseDiagnoser.toReplanAction(cause, "feedback", Set.of("n1"));

        assertThat(action).isInstanceOf(ReplanAction.AcceptPartial.class);
        ReplanAction.AcceptPartial ap = (ReplanAction.AcceptPartial) action;
        assertThat(ap.reason()).contains("Perception unreliable");
        assertThat(ap.reason()).contains("threw");
        assertThat(ap.reason()).contains("cannot trust");
    }

    @Test
    void dispatchPerceptionUnreliableNullCaseReturnsAcceptPartial() {
        // verifierThrew=false 分支（verify 返回 null，非抛异常）
        RootCause cause = new RootCause.PerceptionUnreliable(false);
        ReplanAction action = RootCauseDiagnoser.toReplanAction(cause, "fb", Set.of("n1"));

        assertThat(action).isInstanceOf(ReplanAction.AcceptPartial.class);
        assertThat(((ReplanAction.AcceptPartial) action).reason()).contains("returned null");
    }

    @Test
    void dispatchPlanOrAnswerErrorFewNodesReturnsLocalReplan() {
        // 承重 IFF：≤2 失败节点 → LocalReplan（精确重执行+correction hint 注入）
        // mutation-RED：改阈值 ≤2→≤0 → RED（2 节点场景误入 GlobalReplan）
        RootCause cause = new RootCause.PlanOrAnswerError(Set.of("n1", "n2"));
        String feedback = "两节点输出格式错误";
        ReplanAction action = RootCauseDiagnoser.toReplanAction(cause, feedback, Set.of("n1", "n2"));

        assertThat(action).isInstanceOf(ReplanAction.LocalReplan.class);
        ReplanAction.LocalReplan lr = (ReplanAction.LocalReplan) action;
        assertThat(lr.failedNodes()).containsExactlyInAnyOrder("n1", "n2");
        assertThat(lr.feedback()).isEqualTo(feedback);
    }

    @Test
    void dispatchPlanOrAnswerErrorSingleNodeReturnsLocalReplan() {
        // 边界：单节点失败仍为 LocalReplan
        RootCause cause = new RootCause.PlanOrAnswerError(Set.of("n1"));
        ReplanAction action = RootCauseDiagnoser.toReplanAction(cause, "fb", Set.of("n1"));

        assertThat(action).isInstanceOf(ReplanAction.LocalReplan.class);
    }

    @Test
    void dispatchPlanOrAnswerErrorManyNodesReturnsGlobalReplan() {
        // 承重 IFF：>2 失败节点 → GlobalReplan（局部覆盖不了）
        // mutation-RED：改阈值 >2→>100 → RED（3 节点误入 LocalReplan）
        RootCause cause = new RootCause.PlanOrAnswerError(Set.of("n1", "n2", "n3"));
        ReplanAction action = RootCauseDiagnoser.toReplanAction(cause, "全错", Set.of("n1", "n2", "n3"));

        assertThat(action).isInstanceOf(ReplanAction.GlobalReplan.class);
        ReplanAction.GlobalReplan gr = (ReplanAction.GlobalReplan) action;
        assertThat(gr.feedback()).isEqualTo("全错");
    }

    @Test
    void dispatchPlanOrAnswerErrorEmptyNodesReturnsGlobalReplan() {
        // 承重 IFF：P4 Bug #9 fix——空失败节点集 → GlobalReplan（非 LocalReplan 空集无意义）
        // mutation-RED：剥 empty.isEmpty() yield → RED（空集进 LocalReplan 重执行零节点=no-op）
        RootCause cause = new RootCause.PlanOrAnswerError(Set.of());
        ReplanAction action = RootCauseDiagnoser.toReplanAction(cause, "feedback", Set.of());

        assertThat(action).isInstanceOf(ReplanAction.GlobalReplan.class);
    }

    @Test
    void dispatchPlanOrAnswerErrorNullNodesReturnsGlobalReplan() {
        // 防御：PlanOrAnswerError.nodes()==null → 视为空集 → GlobalReplan
        RootCause cause = new RootCause.PlanOrAnswerError(null);
        ReplanAction action = RootCauseDiagnoser.toReplanAction(cause, "fb", Set.of());

        assertThat(action).isInstanceOf(ReplanAction.GlobalReplan.class);
    }

    // ==================== Pattern-match exhaustiveness ====================

    @Test
    void patternMatchCoversAll3States() {
        // 承重：switch over ReplanAction 穷尽 3 态——删任一 case → 编译红
        ReplanAction[] cases = {
                new ReplanAction.LocalReplan(Set.of("n1"), "fb"),
                new ReplanAction.GlobalReplan("fb"),
                new ReplanAction.AcceptPartial("reason")
        };
        for (ReplanAction ra : cases) {
            String label = switch (ra) {
                case ReplanAction.LocalReplan lr -> "local:" + lr.failedNodes().size();
                case ReplanAction.GlobalReplan gr -> "global:" + gr.feedback();
                case ReplanAction.AcceptPartial ap -> "partial:" + ap.reason();
            };
            assertThat(label).isNotEmpty();
        }
    }
}
