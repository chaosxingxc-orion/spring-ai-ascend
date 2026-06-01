package com.huawei.ascend.service.taskflow.queue;

import java.time.Instant;
import java.util.Objects;

public record QueueRegistration(
        String queueId,
        String tenantId,
        String sessionId,
        String owner,
        Instant createdAt) {

    public QueueRegistration {
        queueId = requireNonBlank(queueId, "queueId");
        tenantId = requireNonBlank(tenantId, "tenantId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        owner = requireNonBlank(owner, "owner");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static QueueRegistration session(String tenantId, String sessionId) {
        return new QueueRegistration(sessionQueueId(tenantId, sessionId),
                tenantId, sessionId, "session", Instant.now());
    }

    public static String sessionQueueId(String tenantId, String sessionId) {
        return requireNonBlank(tenantId, "tenantId") + ":" + requireNonBlank(sessionId, "sessionId");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
