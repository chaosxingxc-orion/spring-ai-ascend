package com.huawei.ascend.runtime.access.protocol.a2a.model;

public record A2aAcceptedResponse(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String taskId,
        boolean accepted,
        String message) {
}

