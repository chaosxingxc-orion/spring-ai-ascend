package com.openjiuwen.runtime.beta.selfheal;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.alpha.verifier.ReplanAction;
import com.openjiuwen.core.alpha.verifier.RootCause;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * RootCause 自愈承重测试——diagnose 3 态映射 IFF + dispatch sealed switch 穷尽 3 态。
 *
 * <p>扁平 @Test（非 @Nested）——让 mvn test 报告 Tests run: 11 外类可见（@Nested surefire 外类报告
 * Tests run: 0 是聚合伪影，易误读为跳过）。
 *
 * <p>承重 IFF（plan V2 轮5）：
 * <ul>
 *   <li>Diagnoser：信号→RootCause 3 态映射确定性（剥任一判定→该态漏诊→RED，mutation-RED 已证 verifyThrew）。</li>
 *   <li>Dispatcher：sealed switch 穷尽 3 态——<b>删任一 case arm → 编译红</b>（类型层 mutation-prove，
 *       比运行时 RED 更早，编译期拦漏分支）。映射确定性（DeviceFailure/Perception→Degrade，
 *       PlanOrAnswer→Replan，剥 token→RED）。</li>
 * </ul>
 */
class RootCauseSelfHealTest {

    // ==================== Diagnoser: 信号→RootCause 3 态映射 IFF ====================

    @Test
    void diagnoseVerifyThrewReturnsPerceptionUnreliable() {
        // mutation-RED：剥 if(verifyThrew) → 此输入进 DeviceFailure 分支 → expected Perception RED
        RootCause cause = RootCauseDiagnoser.diagnose(true, Set.of("n1"), Set.of("n1"));
        assertThat(cause).isInstanceOf(RootCause.PerceptionUnreliable.class);
        assertThat(((RootCause.PerceptionUnreliable) cause).verifierThrew()).isTrue();
    }

    @Test
    void diagnoseToolFailureIntersectionReturnsDeviceFailure() {
        RootCause cause = RootCauseDiagnoser.diagnose(false, Set.of("n1", "n2"), Set.of("n2", "n3"));
        assertThat(cause).isInstanceOf(RootCause.DeviceFailure.class);
        assertThat(((RootCause.DeviceFailure) cause).nodes()).containsExactly("n2");
    }

    @Test
    void diagnoseNoSignalReturnsPlanOrAnswerError() {
        RootCause cause = RootCauseDiagnoser.diagnose(false, Set.of("n1"), Set.of("n2"));
        assertThat(cause).isInstanceOf(RootCause.PlanOrAnswerError.class);
        assertThat(((RootCause.PlanOrAnswerError) cause).nodes()).containsExactly("n2");
    }

    @Test
    void diagnoseNullInputsDegradeSafely() {
        RootCause cause = RootCauseDiagnoser.diagnose(false, null, null);
        assertThat(cause).isInstanceOf(RootCause.PlanOrAnswerError.class);
        assertThat(((RootCause.PlanOrAnswerError) cause).nodes()).isEmpty();
    }

    @Test
    void diagnoseDisjointFailureSetsReturnPlanOrAnswerError() {
        RootCause cause = RootCauseDiagnoser.diagnose(false, Set.of("a"), Set.of("b"));
        assertThat(cause).isInstanceOf(RootCause.PlanOrAnswerError.class);
    }

    // ==================== Dispatcher: sealed switch 穷尽 3 态 ====================
    // 承重（类型层 mutation-prove）：RootCauseDispatcher.dispatch 是 sealed switch expression 覆盖
    // RootCause 全部 3 态。删任一 case arm → 编译红（非运行 RED）。比 mutation-RED 更强——编译期拦漏分支。
    // 手动验证：注释掉任一 case → mvnw compile 失败（switch does not cover all possible input values）。

    @Test
    void dispatchDeviceFailureReturnsDegrade() {
        SelfHealAction action = RootCauseDispatcher.dispatch(new RootCause.DeviceFailure(Set.of("n1")));
        assertThat(action).isInstanceOf(SelfHealAction.Degrade.class);
        assertThat(((SelfHealAction.Degrade) action).reason()).contains("设备故障");
    }

    @Test
    void dispatchPerceptionUnreliableReturnsDegrade() {
        SelfHealAction action = RootCauseDispatcher.dispatch(new RootCause.PerceptionUnreliable(true));
        assertThat(action).isInstanceOf(SelfHealAction.Degrade.class);
        assertThat(((SelfHealAction.Degrade) action).reason()).contains("感知出错");
    }

    @Test
    void dispatchPlanOrAnswerErrorReturnsReplan() {
        SelfHealAction action = RootCauseDispatcher.dispatch(new RootCause.PlanOrAnswerError(Set.of("n1")));
        assertThat(action).isInstanceOf(SelfHealAction.Replan.class);
        assertThat(((SelfHealAction.Replan) action).reason()).contains("图-答案出错");
    }

    // ==================== 端到端: diagnose → dispatch 自愈动作 ====================

    @Test
    void e2eVerifyThrewFlowsToDegrade() {
        SelfHealAction action = RootCauseDispatcher.dispatch(
                RootCauseDiagnoser.diagnose(true, Set.of(), Set.of()));
        assertThat(action).isInstanceOf(SelfHealAction.Degrade.class);
    }

    @Test
    void e2eToolFailureFlowsToDegrade() {
        SelfHealAction action = RootCauseDispatcher.dispatch(
                RootCauseDiagnoser.diagnose(false, Set.of("n1"), Set.of("n1")));
        assertThat(action).isInstanceOf(SelfHealAction.Degrade.class);
    }

    @Test
    void e2eContentErrorFlowsToReplan() {
        SelfHealAction action = RootCauseDispatcher.dispatch(
                RootCauseDiagnoser.diagnose(false, Set.of(), Set.of("n1")));
        assertThat(action).isInstanceOf(SelfHealAction.Replan.class);
    }

    // ==================== AAC toReplanAction: RootCause → ReplanAction ====================
    // 承重（AAC 核心 dispatch ~20 行）：sealed switch 穷尽 3 态 + 阈值规则确定性。
    // 剥任一 case arm → 编译红（类型层 mutation-prove）。

    @Test
    void toReplanActionDeviceFailureIsAcceptPartial() {
        // 承重 IFF：DeviceFailure 永不应重试
        // mutation-RED：在 toReplanAction DeviceFailure arm 改返 LocalReplan → RED
        ReplanAction action = RootCauseDiagnoser.toReplanAction(
                new RootCause.DeviceFailure(Set.of("tool1")), "fb", Set.of("n1"));
        assertThat(action).isInstanceOf(ReplanAction.AcceptPartial.class);
        assertThat(((ReplanAction.AcceptPartial) action).reason()).contains("Device failure");
    }

    @Test
    void toReplanActionPerceptionUnreliableIsAcceptPartial() {
        // 承重 IFF：PerceptionUnreliable 永不应重试
        // mutation-RED：在 toReplanAction PerceptionUnreliable arm 改返 LocalReplan → RED
        ReplanAction action = RootCauseDiagnoser.toReplanAction(
                new RootCause.PerceptionUnreliable(true), "fb", Set.of("n1"));
        assertThat(action).isInstanceOf(ReplanAction.AcceptPartial.class);
        assertThat(((ReplanAction.AcceptPartial) action).reason()).contains("Perception unreliable");
    }

    @Test
    void toReplanActionPlanOrAnswerError2NodesIsLocalReplan() {
        // 承重 IFF：≤2 节点 → LocalReplan（精确重执行）
        // mutation-RED：改阈值 ≤2→<0 → RED
        ReplanAction action = RootCauseDiagnoser.toReplanAction(
                new RootCause.PlanOrAnswerError(Set.of("a", "b")), "hint", Set.of("a", "b"));
        assertThat(action).isInstanceOf(ReplanAction.LocalReplan.class);
        assertThat(((ReplanAction.LocalReplan) action).failedNodes()).hasSize(2);
    }

    @Test
    void toReplanActionPlanOrAnswerError3NodesIsGlobalReplan() {
        // 承重 IFF：>2 节点 → GlobalReplan
        // mutation-RED：改阈值 >2→>10 → RED
        ReplanAction action = RootCauseDiagnoser.toReplanAction(
                new RootCause.PlanOrAnswerError(Set.of("a", "b", "c")), "全错", Set.of("a", "b", "c"));
        assertThat(action).isInstanceOf(ReplanAction.GlobalReplan.class);
    }

    @Test
    void toReplanActionEmptyNodesIsGlobalReplan() {
        // 承重 IFF：P4 Bug #9——空失败集 → GlobalReplan（非 LocalReplan 空集无意义）
        // mutation-RED：剥 empty.isEmpty() yield → RED（空集进 LocalReplan=no-op）
        ReplanAction action = RootCauseDiagnoser.toReplanAction(
                new RootCause.PlanOrAnswerError(Set.of()), "fb", Set.of());
        assertThat(action).isInstanceOf(ReplanAction.GlobalReplan.class);
    }
}
