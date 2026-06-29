package com.openjiuwen.runtime.core.engine;

import com.openjiuwen.core.kernel.model.*;

import java.util.List;
import java.util.Optional;

/**
 * 安全边界——Agent 执行的守护者。
 * 在每个系统调用前后执行安全检查，防止 Agent 行为失控。
 *
 * 契约：
 * - 所有检查必须是纯函数（无副作用），不修改传入的参数
 * - 检查失败时返回 Violation 列表，由调用方决定如何处理
 * - 不抛异常——异常用于系统 bug，违规用于 Agent 行为越界
 */
public interface SafetyBoundary {

    /**
     * 检查工具调用是否被允许。
     * 在 AgentKernel.invokeTool() 执行前调用。
     *
     * @param toolName  待调用的工具名称
     * @param arguments 工具调用参数
     * @param budget    当前预算消耗情况
     * @return 违规列表，空列表表示全部通过
     */
    List<Violation> checkToolCall(ToolName toolName, java.util.Map<String, Object> arguments, BudgetLimits budget);

    /**
     * 检查 LLM 输出是否安全。
     * 在 AgentKernel.think() 返回结果后调用。
     *
     * @param output LLM 的输出文本
     * @return 违规列表，空列表表示全部通过
     */
    List<Violation> checkLLMOutput(String output);

    /**
     * 检查预算是否充足。
     * 在每次系统调用前调用。
     *
     * @param budget 当前预算追踪
     * @return 违规列表，空列表表示预算充足
     */
    List<Violation> checkBudget(BudgetLimits budget);

    /**
     * 检查任务图是否合法。
     * 在 Alpha 策略规划完成后调用。
     *
     * @return 违规列表，空列表表示合法
     */
    default List<Violation> checkTaskGraph(Object taskGraph) {
        return List.of();
    }

    /**
     * 检查成功标准的覆盖情况。
     *
     * Beta 策略在 FinalAnswer 前必须调用此方法，
     * 确保所有 successCriteria 已被 Agent 的执行过程覆盖。
     *
     * 对比逻辑：
     * - successCriteria：任务开始时定义的全部标准（来自 GoalSpec）
     * - verifiedCriteria：Agent 执行过程中已经验证通过的标准
     * - 差集 = 未覆盖的标准 → 每条产生一个 CriteriaNotCovered Violation
     *
     * @param task             任务 ID（用于审计日志）
     * @param successCriteria  全部成功标准
     * @param verifiedCriteria 已验证通过的标准
     * @return 第一个未覆盖标准的 Violation（Optional），空 = 全部通过
     */
    default Optional<Violation> checkCriteriaCoverage(
            TaskId task,
            List<String> successCriteria,
            List<String> verifiedCriteria) {
        // 找出 successCriteria 中不在 verifiedCriteria 里的第一条
        for (String criterion : successCriteria) {
            boolean covered = verifiedCriteria.stream()
                .anyMatch(vc -> vc.equalsIgnoreCase(criterion)
                    || vc.contains(criterion)
                    || criterion.contains(vc));
            if (!covered) {
                return Optional.of(new Violation.CriteriaNotCovered(
                    criterion,
                    "此标准未被 Agent 的执行过程验证覆盖"));
            }
        }
        return Optional.empty();
    }

    /**
     * 批量检查成功标准的覆盖情况——返回所有未覆盖的标准。
     *
     * @param task             任务 ID
     * @param successCriteria  全部成功标准
     * @param verifiedCriteria 已验证通过的标准
     * @return 所有未覆盖标准的 Violation 列表，空 = 全部通过
     */
    default List<Violation> checkCriteriaCoverageAll(
            TaskId task,
            List<String> successCriteria,
            List<String> verifiedCriteria) {
        return successCriteria.stream()
            .filter(criterion -> verifiedCriteria.stream()
                .noneMatch(vc -> vc.equalsIgnoreCase(criterion)
                    || vc.contains(criterion)
                    || criterion.contains(vc)))
            .map(criterion -> (Violation) new Violation.CriteriaNotCovered(
                criterion,
                "此标准未被 Agent 的执行过程验证覆盖"))
            .toList();
    }

    /**
     * 检查 MCP 通信安全性。
     *
     * 在每次 MCP 工具调用前检查 mTLS 是否已建立。
     *
     * @param endpoint MCP 端点地址
     * @param isTlsEstablished TLS 是否已建立
     * @return Violation 如果安全检查未通过
     */
    default Optional<Violation> checkMcpSecurity(String endpoint, boolean isTlsEstablished) {
        if (!isTlsEstablished) {
            return Optional.of(new Violation.McpSecurityViolation(
                endpoint,
                "MCP 通信未使用 mTLS 加密"));
        }
        return Optional.empty();
    }
}
