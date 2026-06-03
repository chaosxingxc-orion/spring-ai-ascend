package com.huawei.ascend.service.access.model;

import java.util.Map;
import java.util.Objects;

public record AccessCancelCommand(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String taskId,
        String reason,
        Map<String, Object> metadata) {

    public AccessCancelCommand {
        tenantId = requireNonBlank(tenantId, "tenantId");
        userId = requireNonBlank(userId, "userId");
        agentId = requireNonBlank(agentId, "agentId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        taskId = requireNonBlank(taskId, "taskId");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
