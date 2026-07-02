package com.openjiuwen.core.kernel.model;

/**
 * yield 原因——Agent 暂停/让出执行权的原因。
 * sealed interface 确保所有暂停场景都被显式处理。
 */
public sealed interface YieldReason
    permits YieldReason.WaitingForApproval,
            YieldReason.WaitingForExternalInput,
            YieldReason.BudgetExceeded,
            YieldReason.Checkpointed,
            YieldReason.MaxRetriesReached {

    /** 人类可读的描述 */
    String description();

    /**
     * 等待人工审批。
     * Alpha 策略在遇到 ApprovalGate 时触发。
     */
    record WaitingForApproval(String gateId, String description) implements YieldReason {}

    /**
     * 等待外部系统输入。
     * 如：等待异步 API 响应、等待用户补充信息。
     */
    record WaitingForExternalInput(String source, String description) implements YieldReason {}

    /**
     * 预算耗尽。
     * Agent 执行被强制中断，保存检查点后可追加预算恢复。
     */
    record BudgetExceeded(String description) implements YieldReason {
        public BudgetExceeded {
            if (description == null) description = "预算已耗尽";
        }
    }

    /**
     * 检查点保存后的正常让出。
     * 长流程场景，Agent 主动让出执行权，下次从检查点恢复。
     */
    record Checkpointed(CheckpointId checkpointId, String description) implements YieldReason {}

    /**
     * 最大重试次数已用尽。
     * 验证阶段多次失败后，放弃继续重试。
     */
    record MaxRetriesReached(int retries, String description) implements YieldReason {}
}
