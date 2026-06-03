package com.huawei.ascend.service.session.model;

import com.huawei.ascend.service.schema.Message;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Session(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        List<Message> currentUserInput,
        Instant createdAt,
        Instant updatedAt,
        Instant lastAccessedAt,
        Instant expiresAt) {

    public Session {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(sessionId, "sessionId");
        currentUserInput = currentUserInput == null ? List.of() : List.copyOf(currentUserInput);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(lastAccessedAt, "lastAccessedAt");
    }

    public SessionKey key() {
        return new SessionKey(tenantId, sessionId);
    }
}
