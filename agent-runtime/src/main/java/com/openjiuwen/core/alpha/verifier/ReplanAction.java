package com.openjiuwen.core.alpha.verifier;

import java.util.Set;

/**
 * PEV 自愈调度动作——{@link RootCauseDiagnoser#toReplanAction(RootCause, String, Set)} 的输出，
 * PEV 主循环的 switch 输入。这是 AAC 的 dispatch 层输出类型。
 *
 * <p>三层类型关系（AAC 核心洞察）：
 * <ul>
 *   <li>{@link RootCause}（诊断输出）：<b>为什么</b> verify 失败？——3 态纯函数确定性分类。</li>
 *   <li><b>ReplanAction</b>（调度输出）：<b>做什么</b>？——携带执行所需数据（哪个节点、什么 feedback）。</li>
 *   <li>{@link ReplanStrategy}（verifier 推荐）：<b>建议</b>什么策略？——携带策略参数（maxRounds），不含具体节点列表。</li>
 *   <li>{@code SelfHealAction}（1.0 ReActAgent bridge）：Rails 怎么响应？——2 态 Degrade/Replan，已 @Deprecated。</li>
 * </ul>
 *
 * <p>sealed 3 态穷举——switch over ReplanAction 删任一 case arm → 编译红（编译器当防火墙）。
 * 这压缩了 gepa3 P8 MinorityAgentEngine 的 dispatch() 逻辑和 shouldDowngradeGlobalReplan() guard
 * 到一个 sealed switch 表达式。
 *
 * <p>映射契约：DeviceFailure/PerceptionUnreliable → AcceptPartial（永不应重试）；
 * PlanOrAnswerError 少量失败 → LocalReplan（精确重执行+correction hint）；
 * PlanOrAnswerError 大量/空失败 → GlobalReplan（重新规划）。
 *
 * @see RootCause
 * @see com.openjiuwen.runtime.beta.selfheal.RootCauseDiagnoser#toReplanAction(RootCause, String, Set)
 */
public sealed interface ReplanAction
        permits ReplanAction.LocalReplan,
                ReplanAction.GlobalReplan,
                ReplanAction.AcceptPartial {

    /**
     * 局部重执行：只重做失败的节点（及其下游依赖），注入 correction hint 供 LLM 自纠正。
     *
     * @param failedNodes 需重执行的失败节点 ID 集（精确列表，非策略建议）
     * @param feedback    verify 反馈（correction hint），注入到 LLM_CALL 节点的 &lt;correction&gt; XML 块
     */
    record LocalReplan(Set<String> failedNodes, String feedback) implements ReplanAction {}

    /**
     * 全局重规划：丢弃整个 TaskGraph，重新调用 Planner 生成新 Plan。
     *
     * @param feedback verify 反馈，作为新 Plan 的上下文提示
     */
    record GlobalReplan(String feedback) implements ReplanAction {}

    /**
     * 接受部分结果：replan 无法修复（设备故障/感知不可靠），诚实降级接受已完成结果。
     *
     * @param reason 降级原因（人类可读的诊断说明）
     */
    record AcceptPartial(String reason) implements ReplanAction {}
}
