package com.openjiuwen.core.alpha.verifier;

import java.util.Set;

/**
 * verify 失败的根因诊断——自愈的决策上游（根因驱动：先诊断失败属于哪一类，再据此选自愈策略）。
 *
 * <p>三类根因：
 * <ul>
 *   <li>{@link DeviceFailure} —— 设备故障（工具返错/超时）。<b>replan 无效</b>：同输入重跑还错，重试纯浪费轮次 → 降级。</li>
 *   <li>{@link PerceptionUnreliable} —— 感知出错（verifier 异常/不可靠）。<b>别盲信 FAILED</b>：verifier 不可信时其
 *       FAILED 判定也不可信 → 降级放行。</li>
 *   <li>{@link PlanOrAnswerError} —— 图-答案出错（plan/LLM 内容错）。<b>replan 救得了</b> → replan。</li>
 * </ul>
 *
 * <p>sealed record（带数据）让 dispatch 漏分支<b>编译红</b>——类型层证明（比运行时测试 RED 更早，
 * 编译期就拦漏分支）。dispatch switch 穷尽 3 态，删一态 arm 即编译错。
 */
public sealed interface RootCause permits
        RootCause.DeviceFailure,
        RootCause.PlanOrAnswerError,
        RootCause.PerceptionUnreliable {

    /** 设备故障：工具返错/超时——replan 无效。nodes=既是工具失败、又被 verify 判失败的节点集（诊断证据）。 */
    record DeviceFailure(Set<String> nodes) implements RootCause {}

    /** 图-答案出错：plan/LLM 内容错——replan 救得了。nodes=verify 判失败的节点集。 */
    record PlanOrAnswerError(Set<String> nodes) implements RootCause {}

    /** 感知出错：verifier 异常/不可靠——别盲信 FAILED。verifierThrew=true=verify 抛异常；false=verify 返回 null。 */
    record PerceptionUnreliable(boolean verifierThrew) implements RootCause {}
}
