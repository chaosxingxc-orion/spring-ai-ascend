package com.openjiuwen.core.kernel.model;

/**
 * 预算限额——运行时消耗追踪。
 * 每次 AgentKernel 系统调用后更新，用于实时判断是否超限。
 *
 * @param budget         原始预算配置
 * @param usedLLMCalls   已消耗的 LLM 调用次数
 * @param usedToolCalls  已消耗的工具调用次数
 * @param usedTokens     已消耗的 Token 数
 * @param elapsedMillis  已消耗的时间（毫秒）
 */
public record BudgetLimits(
    Budget budget,
    int usedLLMCalls,
    int usedToolCalls,
    long usedTokens,
    long elapsedMillis
) {

    /** 从预算创建初始的零消耗追踪 */
    public static BudgetLimits start(Budget budget) {
        return new BudgetLimits(budget, 0, 0, 0L, 0L);
    }

    /** 记录一次 LLM 调用 */
    public BudgetLimits recordLLMCall(int tokens) {
        return new BudgetLimits(budget, usedLLMCalls + 1, usedToolCalls, usedTokens + tokens, elapsedMillis);
    }

    /** 记录一次工具调用 */
    public BudgetLimits recordToolCall() {
        return new BudgetLimits(budget, usedLLMCalls, usedToolCalls + 1, usedTokens, elapsedMillis);
    }

    /** 更新耗时 */
    public BudgetLimits withElapsed(long millis) {
        return new BudgetLimits(budget, usedLLMCalls, usedToolCalls, usedTokens, millis);
    }

    /** 是否已超出任何预算限制 */
    public boolean isExceeded() {
        return usedLLMCalls >= budget.maxLLMCalls()
            || usedToolCalls >= budget.maxToolCalls()
            || usedTokens >= budget.maxTokens()
            || (budget.timeoutMillis() > 0 && elapsedMillis >= budget.timeoutMillis());
    }
}
