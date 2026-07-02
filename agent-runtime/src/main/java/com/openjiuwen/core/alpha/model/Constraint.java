package com.openjiuwen.core.alpha.model;

/**
 * 约束——Alpha 策略中的执行约束。
 *
 * 约束是 PEV 模型的"安全网"：
 * - 在规划阶段：约束限制 TaskGraph 的结构
 * - 在执行阶段：约束限制每个节点的行为
 * - 在验证阶段：约束是结果检查的判定标准
 *
 * sealed interface 确保所有约束类型都被显式处理。
 */
public sealed interface Constraint
    permits Constraint.MaxStepsConstraint,
            Constraint.RequiredToolConstraint,
            Constraint.OutputFormatConstraint,
            Constraint.ApprovalConstraint {

    /** 约束 ID */
    String constraintId();

    /** 约束描述（人类可读） */
    String description();

    /** 是否为硬约束（违反则终止） */
    boolean isHard();

    /**
     * 最大步骤约束：限制任务图的最大节点数。
     * 防止 LLM 规划出过于复杂的任务图。
     */
    record MaxStepsConstraint(
        String constraintId,
        String description,
        int maxSteps
    ) implements Constraint {
        public MaxStepsConstraint(int maxSteps) {
            this("max-steps", "最大步骤数: " + maxSteps, maxSteps);
        }
        @Override public boolean isHard() { return true; }
    }

    /**
     * 必需工具约束：指定必须使用的工具。
     * 规划阶段检查 TaskGraph 是否包含了这些工具节点。
     */
    record RequiredToolConstraint(
        String constraintId,
        String description,
        java.util.Set<String> requiredTools
    ) implements Constraint {
        @Override public boolean isHard() { return true; }
    }

    /**
     * 输出格式约束：要求最终输出符合指定格式。
     * 验证阶段用此约束检查输出是否满足格式要求。
     */
    record OutputFormatConstraint(
        String constraintId,
        String description,
        String formatDescription
    ) implements Constraint {
        @Override public boolean isHard() { return false; }
    }

    /**
     * 审批约束：指定哪些操作需要人工审批。
     * 执行阶段遇到审批门时暂停，等待人工确认后继续。
     */
    record ApprovalConstraint(
        String constraintId,
        String description,
        java.util.Set<String> approvalTools
    ) implements Constraint {
        @Override public boolean isHard() { return true; }
    }
}
