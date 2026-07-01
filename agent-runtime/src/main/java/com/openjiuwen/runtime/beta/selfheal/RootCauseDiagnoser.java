package com.openjiuwen.runtime.beta.selfheal;

import com.openjiuwen.core.alpha.verifier.ReplanAction;
import com.openjiuwen.core.alpha.verifier.RootCause;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 根因诊断——从确定性信号诊断 verify 失败属于 {@link RootCause} 哪一态。纯函数（移植自 alpha
 * AlphaStrategy.diagnoseRootCause）。
 *
 * <p>诊断优先级（确定性信号，非 LLM 猜测）：
 * <ol>
 *   <li>verifyThrew（verifier 抛异常）→ {@link RootCause.PerceptionUnreliable}（最高优先：verifier 不可信时
 *       其 FAILED 判定也不可信，别盲信）。</li>
 *   <li>verify 失败节点 ∩ 工具失败节点 非空 → {@link RootCause.DeviceFailure}（节点执行本身失败，replan 无效）。</li>
 *   <li>排除以上 → {@link RootCause.PlanOrAnswerError}（内容错，replan 救得了）。</li>
 * </ol>
 *
 * <p>承重 IFF：3 态映射由信号确定性驱动（剥任一判定→该态漏诊→RED）。
 */
public final class RootCauseDiagnoser {

    private RootCauseDiagnoser() {
    }

    /**
     * @param verifyThrew      verifier 是否抛异常（感知失效信号）
     * @param failedToolNodes  工具失败的节点集（设备故障信号，null 视为空）
     * @param verifyFailedNodes verify 判失败的节点集（null 视为空）
     */
    public static RootCause diagnose(
            boolean verifyThrew, Set<String> failedToolNodes, Set<String> verifyFailedNodes) {
        if (verifyThrew) {
            return new RootCause.PerceptionUnreliable(true);
        }
        Set<String> hit = intersection(failedToolNodes, verifyFailedNodes);
        if (!hit.isEmpty()) {
            return new RootCause.DeviceFailure(hit);
        }
        return new RootCause.PlanOrAnswerError(
                verifyFailedNodes == null ? Set.of() : Set.copyOf(verifyFailedNodes));
    }

    /**
     * AAC dispatch 纯函数：RootCause → ReplanAction。
     *
     * <p>这是结论AAC 的核心 ~20 行 dispatch——将诊断结果（为什么失败）映射为可执行动作（做什么）。
     * sealed switch 穷举 3 态：删任一 case arm → 编译红（编译器当防火墙）。
     *
     * <p>映射规则（根因驱动）：
     * <ol>
     *   <li>DeviceFailure → AcceptPartial（工具/基础设施故障，replan 无法修复）。</li>
     *   <li>PerceptionUnreliable → AcceptPartial（verifier 不可信，别盲信 FAILED 判定）。</li>
     *   <li>PlanOrAnswerError 少量失败节点（≤2）→ LocalReplan（精确重执行 + feedback 注入）。</li>
     *   <li>PlanOrAnswerError 大量失败节点（>2）或空失败节点集 → GlobalReplan（重新规划整个图）。</li>
     * </ol>
     *
     * <p>承重 IFF：DeviceFailure/PerceptionUnreliable 永不应返回 Replan（剥此约束 → RED——设备故障
     * 场景 replan 浪费轮次不修复，恒失败）。PlanOrAnswerError 永不应 AcceptPartial（剥 → RED——
     * 内容错 replan 可修复，降级过早放弃）。
     *
     * @param cause       根因（来自 {@link #diagnose(boolean, Set, Set)}）
     * @param feedback    verify 反馈（作为 correction hint 或新 plan 上下文）
     * @param failedNodes verify 判失败的节点集（用于判断局部/全局 replan 阈值）
     * @return 可执行的调度动作
     */
    public static ReplanAction toReplanAction(RootCause cause, String feedback, Set<String> failedNodes) {
        return switch (cause) {
            case RootCause.DeviceFailure d ->
                    new ReplanAction.AcceptPartial("Device failure: " + d.nodes()
                            + " — replan cannot fix broken tools/infra");
            case RootCause.PerceptionUnreliable p ->
                    new ReplanAction.AcceptPartial("Perception unreliable: verifier "
                            + (p.verifierThrew() ? "threw" : "returned null")
                            + " — cannot trust its FAILED verdict");
            case RootCause.PlanOrAnswerError pe -> {
                Set<String> nodes = pe.nodes() == null ? Set.of() : pe.nodes();
                if (nodes.isEmpty()) {
                    yield new ReplanAction.GlobalReplan(feedback);
                }
                if (nodes.size() <= 2) {
                    yield new ReplanAction.LocalReplan(nodes, feedback);
                }
                yield new ReplanAction.GlobalReplan(feedback);
            }
        };
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>(a);
        result.retainAll(b);
        return result;
    }
}
