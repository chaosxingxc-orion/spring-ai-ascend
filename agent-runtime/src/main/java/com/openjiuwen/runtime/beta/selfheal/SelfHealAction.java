package com.openjiuwen.runtime.beta.selfheal;

/**
 * 根因自愈动作——{@link RootCauseDispatcher} 的输出，根因 dispatch 后选定的自愈策略。
 *
 * <p>sealed 2 态（RootCause 3 态经 dispatch 收敛到 2 动作）：
 * <ul>
 *   <li>{@link Degrade} —— 降级终态（COMPLETED-degraded）：replan 无效（DeviceFailure）或别盲信 FAILED
 *       （PerceptionUnreliable）时，诚实标记降级，不浪费轮次重试。</li>
 *   <li>{@link Replan} —— 重规划：图-答案出错（PlanOrAnswerError）时 replan 救得了。</li>
 * </ul>
 *
 * <p>嫁接 1.0 ReActAgent 时：Degrade → afterModelCall requestForceFinish(degraded)；
 * Replan → 复用 ReplanRail（replan 工具计数/超限）或直接放行让 agent 换策略。
 *
 * @deprecated PEV 引擎请用 {@link com.openjiuwen.core.alpha.verifier.ReplanAction}（3 态 dispatch
 * 输出，携带执行数据）。SelfHealAction 保留为 1.0 ReActAgent bridge（Rails 的 forceFinish 协议），
 * 不做 Breaking Change 移除。
 */
@Deprecated(since = "0.2.0", forRemoval = false)
public sealed interface SelfHealAction permits
        SelfHealAction.Degrade,
        SelfHealAction.Replan {

    /** 降级：replan 无效或别盲信 FAILED → 降级终态（诚实标记，不假装修正）。 */
    record Degrade(String reason) implements SelfHealAction {}

    /** 重规划：图-答案出错，replan 救得了。 */
    record Replan(String reason) implements SelfHealAction {}
}
