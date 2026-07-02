package com.openjiuwen.runtime.beta.selfheal;

import com.openjiuwen.core.alpha.verifier.RootCause;

/**
 * 根因 dispatch——{@link RootCause} → {@link SelfHealAction}（sealed switch 穷尽 3 态）。纯函数
 * （移植自 alpha AlphaStrategy 根因 dispatch）。
 *
 * <p>sealed switch 穷尽：{@link RootCause} 是 sealed interface，本方法 switch expression 覆盖全部 3 态，
 * <b>删任一 case arm → 编译红</b>（类型层证明，比运行时测试 RED 更早，编译期拦漏分支）。
 *
 * <ul>
 *   <li>{@link RootCause.DeviceFailure} → {@link SelfHealAction.Degrade}（replan 无效）。</li>
 *   <li>{@link RootCause.PerceptionUnreliable} → {@link SelfHealAction.Degrade}（别盲信 FAILED）。</li>
 *   <li>{@link RootCause.PlanOrAnswerError} → {@link SelfHealAction.Replan}（replan 救得了）。</li>
 * </ul>
 *
 * <p>契约（IFF）：3 态→2 动作映射确定性（剥 case arm→编译红；改映射→对应测试 RED）。
 */
public final class RootCauseDispatcher {

    private RootCauseDispatcher() {
    }

    public static SelfHealAction dispatch(RootCause cause) {
        return switch (cause) {
            case RootCause.DeviceFailure df -> new SelfHealAction.Degrade(
                    "设备故障（工具失败节点: " + df.nodes() + "），replan 无效 → 降级");
            case RootCause.PerceptionUnreliable pu -> new SelfHealAction.Degrade(
                    "感知出错（verifier " + (pu.verifierThrew() ? "抛异常" : "返回 null")
                            + "），别盲信 FAILED → 降级放行");
            case RootCause.PlanOrAnswerError pa -> new SelfHealAction.Replan(
                    "图-答案出错（失败节点: " + pa.nodes() + "），replan 救得了");
        };
    }
}
