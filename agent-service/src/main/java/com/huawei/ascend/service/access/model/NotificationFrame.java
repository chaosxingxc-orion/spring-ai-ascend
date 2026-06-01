package com.huawei.ascend.service.access.model;

import java.util.Objects;

public record NotificationFrame(
        String tenantId,
        String sessionId,
        String taskId,
        NotificationType type,
        Object payload,
        boolean terminal) {

    public NotificationFrame {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(type, "type");
    }
}


