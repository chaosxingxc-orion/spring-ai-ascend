package com.huawei.ascend.service.session.model;

import java.time.Instant;
import java.util.Objects;

public record Session(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        Instant createdAt,
        Instant updatedAt,
        Instant lastAccessedAt,
        Instant expiresAt) {

    public Session {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(lastAccessedAt, "lastAccessedAt");
    }

    public SessionKey key() {
        return new SessionKey(tenantId, sessionId);
    }
}
