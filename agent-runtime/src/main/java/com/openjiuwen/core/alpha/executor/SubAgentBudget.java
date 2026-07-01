package com.openjiuwen.core.alpha.executor;

import com.openjiuwen.core.kernel.model.Budget;

/**
 * 子 Agent 预算分配策略——SUB_AGENT 节点执行时的预算切片方式。
 *
 * <p>三种分配策略：
 * <ul>
 *   <li>PROPORTIONAL：按剩余预算的比例分配</li>
 *   <li>FIXED：固定预算</li>
 *   <li>INHERIT：继承父 Agent 的预算（减去已消耗部分）</li>
 * </ul>
 */
public sealed interface SubAgentBudget
    permits SubAgentBudget.Proportional,
            SubAgentBudget.Fixed,
            SubAgentBudget.Inherit {

    /** 按比例分配。ratio 在 (0, 1] 范围。 */
    record Proportional(double ratio) implements SubAgentBudget {
        public Proportional {
            if (ratio <= 0 || ratio > 1) {
                throw new IllegalArgumentException("ratio 必须在 (0, 1] 范围内");
            }
        }
    }

    /** 固定预算。 */
    record Fixed(Budget budget) implements SubAgentBudget {}

    /** 继承父 Agent 预算。 */
    record Inherit() implements SubAgentBudget {}
}
