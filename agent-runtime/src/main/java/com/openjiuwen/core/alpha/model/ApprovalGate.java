package com.openjiuwen.core.alpha.model;

import com.openjiuwen.core.kernel.model.ToolName;
import java.util.Map;

/**
 * 审批门——执行过程中的人工审批节点。
 *
 * <p>当 ExecutionPolicy 中配置了审批约束时，Alpha 策略在执行到
 * 指定工具调用前会暂停，生成 ApprovalGate，等待人工确认。
 */
public record ApprovalGate(
    String gateId,
    ToolName toolName,
    String description,
    Map<String, Object> proposedArguments,
    ApprovalStatus status,
    String reviewerComment
) {
    public enum ApprovalStatus {
        PENDING, APPROVED, DENIED, MODIFIED
    }

    public static ApprovalGate pending(String gateId, ToolName toolName,
                                        String description,
                                        Map<String, Object> proposedArguments) {
        return new ApprovalGate(gateId, toolName, description, proposedArguments,
            ApprovalStatus.PENDING, null);
    }

    public ApprovalGate approve(String comment) {
        return new ApprovalGate(gateId, toolName, description, proposedArguments,
            ApprovalStatus.APPROVED, comment);
    }

    public ApprovalGate deny(String reason) {
        return new ApprovalGate(gateId, toolName, description, proposedArguments,
            ApprovalStatus.DENIED, reason);
    }
}
