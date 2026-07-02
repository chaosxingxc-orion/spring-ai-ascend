package com.openjiuwen.core.alpha.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plan 阶段的输入——结构化的规划目标。
 *
 * 设计决策：Planner 接受 PlanGoal 而非纯文本。
 * 原因：
 * 1. 结构化输入让 Planner 可以做预算可行性检查、约束预校验
 * 2. successCriteria 在 Verify 阶段被复用，避免重复解析
 * 3. 开发者可以通过 API 直接构造 PlanGoal，跳过 LLM 解析
 *
 * 同时支持纯文本降级：PlanGoal.of("raw text") 创建最小结构化输入。
 */
public record PlanGoal(
    String description,
    List<String> successCriteria,
    Set<String> availableTools,
    Map<String, String> context,
    PlanBudgetHint budgetHint
) {

    public PlanGoal {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("目标描述不能为空");
        }
        successCriteria = successCriteria == null ? List.of() : List.copyOf(successCriteria);
        availableTools = availableTools == null ? Set.of() : Set.copyOf(availableTools);
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    /** 纯文本快捷构造 */
    public static PlanGoal of(String description) {
        return new PlanGoal(description, List.of(), Set.of(), Map.of(), null);
    }

    /** 带成功标准 */
    public static PlanGoal of(String description, List<String> successCriteria) {
        return new PlanGoal(description, successCriteria, Set.of(), Map.of(), null);
    }

    /** 完整构造 */
    public static PlanGoal of(String description, List<String> successCriteria,
                              Set<String> availableTools, Map<String, String> context) {
        return new PlanGoal(description, successCriteria, availableTools, context, null);
    }

    /**
     * 预算提示——帮助 Planner 做预算可行性检查。
     * 不是硬限制（硬限制由 BudgetLimits 控制），而是 Planner 生成 TaskGraph 时的参考。
     */
    public record PlanBudgetHint(
        int estimatedLLMCalls,
        int estimatedToolCalls,
        long estimatedTokens
    ) {}
}
