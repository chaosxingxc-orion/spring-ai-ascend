package com.huawei.ascend.service.access.model;

import java.util.Objects;

public record AccessAcceptedResponse(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String taskId,
        boolean accepted,
        String message) {

    public AccessAcceptedResponse {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(taskId, "taskId");
    }
}


