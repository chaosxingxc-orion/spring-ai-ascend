package com.huawei.ascend.service.session.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Session(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        long version,
        List<SessionMessage> messages,
        Map<String, Object> state,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt,
        Instant lastAccessedAt,
        Instant expiresAt) {

    public Session {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(sessionId, "sessionId");
        messages = messages == null ? List.of() : List.copyOf(messages);
        state = state == null ? Map.of() : Map.copyOf(state);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(lastAccessedAt, "lastAccessedAt");
    }

    public SessionKey key() {
        return new SessionKey(tenantId, sessionId);
    }
}
