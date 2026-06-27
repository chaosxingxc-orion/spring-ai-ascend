package com.huawei.ascend.examples.workmate.approval.dto;

import com.huawei.ascend.examples.workmate.approval.ApprovalGate.PendingApproval;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PendingApprovalResponse(
        UUID approvalId,
        UUID sessionId,
        String taskId,
        String toolName,
        String riskLevel,
        String reason,
        String summary,
        Map<String, Object> args,
        Instant createdAt) {

    public static PendingApprovalResponse from(PendingApproval pending) {
        return new PendingApprovalResponse(
                pending.id(),
                pending.sessionId(),
                pending.taskId(),
                pending.toolName(),
                pending.risk().level(),
                pending.risk().reason(),
                pending.risk().summary(),
                pending.args(),
                pending.createdAt());
    }
}
