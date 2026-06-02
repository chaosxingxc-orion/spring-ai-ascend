package com.huawei.ascend.service.session.core;

import com.huawei.ascend.service.session.api.SessionManager;
import com.huawei.ascend.service.session.model.Session;
import com.huawei.ascend.service.session.model.SessionKey;
import com.huawei.ascend.service.session.store.SessionStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SessionManagerImpl implements SessionManager {

    private final SessionStore sessionStore;
    private final Clock clock;
    private final Duration ttl;

    public SessionManagerImpl(SessionStore sessionStore, Clock clock, Duration ttl) {
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = ttl;
    }

    @Override
    public Session loadOrCreate(String tenantId, String userId, String agentId, String sessionId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(agentId, "agentId");
        String resolvedSessionId = sessionId == null || sessionId.isBlank()
                ? UUID.randomUUID().toString()
                : sessionId;
        SessionKey key = new SessionKey(tenantId, resolvedSessionId);
        Optional<Session> existing = sessionStore.find(key);
        if (existing.isPresent()) {
            return touch(key);
        }
        Instant now = clock.instant();
        Instant expiresAt = ttl == null ? null : now.plus(ttl);
        return sessionStore.save(new Session(
                tenantId,
                userId,
                agentId,
                resolvedSessionId,
                now,
                now,
                now,
                expiresAt));
    }

    @Override
    public Optional<Session> get(String tenantId, String sessionId) {
        return sessionStore.find(new SessionKey(tenantId, sessionId));
    }

    @Override
    public boolean exists(String tenantId, String sessionId) {
        return sessionStore.find(new SessionKey(tenantId, sessionId)).isPresent();
    }

    @Override
    public void delete(String tenantId, String sessionId) {
        sessionStore.remove(new SessionKey(tenantId, sessionId));
    }

    private Session touch(SessionKey key) {
        Session session = sessionStore.find(key)
                .orElseThrow(() -> new IllegalStateException("Session not found: " + key));
        return sessionStore.save(withTimestamps(session, session.updatedAt()));
    }

    private Session withTimestamps(Session session, Instant updatedAt) {
        Instant now = clock.instant();
        return new Session(
                session.tenantId(),
                session.userId(),
                session.agentId(),
                session.sessionId(),
                session.createdAt(),
                updatedAt,
                now,
                expiresAt(now));
    }

    private Instant expiresAt(Instant now) {
        return ttl == null ? null : now.plus(ttl);
    }
}
