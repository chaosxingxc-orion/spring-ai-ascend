package com.huawei.ascend.service.session.core;

import com.huawei.ascend.service.schema.Message;
import com.huawei.ascend.service.session.api.SessionManager;
import com.huawei.ascend.service.session.model.Session;
import com.huawei.ascend.service.session.model.SessionKey;
import com.huawei.ascend.service.session.store.SessionStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionManagerImpl implements SessionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManagerImpl.class);

    private final SessionStore sessionStore;
    private final Clock clock;
    private final Duration ttl;

    public SessionManagerImpl(SessionStore sessionStore, Clock clock, Duration ttl) {
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = ttl;
    }

    @Override
    public Session loadOrCreate(
            String tenantId,
            String userId,
            String agentId,
            String sessionId,
            List<Message> currentUserInput) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(agentId, "agentId");
        List<Message> safeCurrentUserInput =
                currentUserInput == null ? List.of() : List.copyOf(currentUserInput);
        String resolvedSessionId = sessionId == null || sessionId.isBlank()
                ? UUID.randomUUID().toString()
                : sessionId;
        SessionKey key = new SessionKey(tenantId, resolvedSessionId);
        Optional<Session> existing = sessionStore.find(key);
        if (existing.isPresent()) {
            LOGGER.info("session touch tenantId={} userId={} agentId={} sessionId={} inputMessages={}",
                    tenantId, userId, agentId, resolvedSessionId, safeCurrentUserInput.size());
            return touch(key, safeCurrentUserInput);
        }
        Instant now = clock.instant();
        Instant expiresAt = ttl == null ? null : now.plus(ttl);
        LOGGER.info("session create tenantId={} userId={} agentId={} sessionId={} inputMessages={}",
                tenantId, userId, agentId, resolvedSessionId, safeCurrentUserInput.size());
        return sessionStore.save(new Session(
                tenantId,
                userId,
                agentId,
                resolvedSessionId,
                safeCurrentUserInput,
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

    private Session touch(SessionKey key, List<Message> currentUserInput) {
        Session session = sessionStore.find(key)
                .orElseThrow(() -> new IllegalStateException("Session not found: " + key));
        return sessionStore.save(withTimestamps(session, currentUserInput));
    }

    private Session withTimestamps(Session session, List<Message> currentUserInput) {
        Instant now = clock.instant();
        return new Session(
                session.tenantId(),
                session.userId(),
                session.agentId(),
                session.sessionId(),
                currentUserInput,
                session.createdAt(),
                now,
                now,
                expiresAt(now));
    }

    private Instant expiresAt(Instant now) {
        return ttl == null ? null : now.plus(ttl);
    }
}
