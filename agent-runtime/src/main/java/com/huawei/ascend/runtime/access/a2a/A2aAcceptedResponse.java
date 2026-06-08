package com.huawei.ascend.runtime.access.a2a;

public record A2aAcceptedResponse(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String taskId,
        boolean accepted,
        String message) {
}

