package com.huawei.ascend.service.access.model;

import java.util.Objects;

public record AccessIntent(
        AccessOperation operation,
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String query,
        String idempotencyKey,
        Object payload) {

    public AccessIntent {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(agentId, "agentId");
    }
}


