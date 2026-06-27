package com.huawei.ascend.examples.workmate.approval.dto;

import java.util.UUID;

public record ApprovalResponse(UUID approvalId, String decision, UUID sessionId) {
}
