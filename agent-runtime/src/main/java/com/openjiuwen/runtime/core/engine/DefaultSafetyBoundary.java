package com.openjiuwen.runtime.core.engine;

import com.openjiuwen.core.kernel.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SafetyBoundary 默认实现。
 *
 * 提供基础的安全检查能力：
 * - 预算检查：超限则返回 BudgetViolation
 * - 工具权限检查：未注册工具返回 UnauthorizedAction
 * - 输出安全检查：敏感信息正则匹配返回 DataLeakViolation
 * - 成功标准覆盖检查：未覆盖返回 CriteriaNotCovered
 * - MCP 安全检查：未加密返回 McpSecurityViolation
 *
 * 企业可继承此类，覆盖 check 方法，添加自定义安全规则。
 */
public class DefaultSafetyBoundary implements SafetyBoundary {

    /** 工具白名单。空 = 允许所有已注册工具 */
    private final Set<ToolName> allowedTools;

    /** 敏感信息正则模式列表 */
    private final List<Pattern> sensitivePatterns;

    public DefaultSafetyBoundary() {
        this(Set.of(), List.of());
    }

    public DefaultSafetyBoundary(Set<ToolName> allowedTools, List<Pattern> sensitivePatterns) {
        this.allowedTools = allowedTools;
        this.sensitivePatterns = sensitivePatterns;
    }

    @Override
    public List<Violation> checkToolCall(ToolName toolName, Map<String, Object> arguments, BudgetLimits budget) {
        List<Violation> violations = new ArrayList<>();

        // 预算检查
        violations.addAll(checkBudget(budget));

        // 工具白名单检查
        if (!allowedTools.isEmpty() && !allowedTools.contains(toolName)) {
            violations.add(new Violation.UnauthorizedAction(
                "工具 " + toolName + " 不在允许列表中",
                "将该工具添加到允许列表，或使用已授权的替代工具"
            ));
        }

        return violations;
    }

    @Override
    public List<Violation> checkLLMOutput(String output) {
        List<Violation> violations = new ArrayList<>();

        if (output == null) return violations;

        // 敏感信息检查
        for (Pattern pattern : sensitivePatterns) {
            if (pattern.matcher(output).find()) {
                violations.add(new Violation.DataLeakViolation(
                    "LLM 输出中包含匹配敏感模式的内容",
                    "检查系统提示，确保 LLM 不会输出敏感信息"
                ));
                break; // 发现一个即上报，不重复
            }
        }

        return violations;
    }

    @Override
    public List<Violation> checkBudget(BudgetLimits budget) {
        if (budget.isExceeded()) {
            return List.of(new Violation.BudgetViolation(
                "预算已耗尽: LLM调用=" + budget.usedLLMCalls() + "/" + budget.budget().maxLLMCalls()
                    + " 工具调用=" + budget.usedToolCalls() + "/" + budget.budget().maxToolCalls()
                    + " tokens=" + budget.usedTokens() + "/" + budget.budget().maxTokens(),
                "增加预算限制，或优化 Agent 策略减少资源消耗"
            ));
        }
        return List.of();
    }

    @Override
    public Optional<Violation> checkCriteriaCoverage(
            TaskId task,
            List<String> successCriteria,
            List<String> verifiedCriteria) {
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

    @Override
    public List<Violation> checkCriteriaCoverageAll(
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

    @Override
    public Optional<Violation> checkMcpSecurity(String endpoint, boolean isTlsEstablished) {
        if (!isTlsEstablished) {
            return Optional.of(new Violation.McpSecurityViolation(
                endpoint,
                "MCP 通信未使用 mTLS 加密"));
        }
        return Optional.empty();
    }
}
