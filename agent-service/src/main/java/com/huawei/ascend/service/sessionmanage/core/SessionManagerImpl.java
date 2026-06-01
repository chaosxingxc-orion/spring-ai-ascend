package com.huawei.ascend.service.sessionmanage.core;

import com.huawei.ascend.service.sessionmanage.api.SessionManager;
import com.huawei.ascend.service.sessionmanage.model.Session;
import com.huawei.ascend.service.sessionmanage.model.SessionKey;
import com.huawei.ascend.service.sessionmanage.model.SessionMessage;
import com.huawei.ascend.service.sessionmanage.model.Task;
import com.huawei.ascend.service.sessionmanage.store.SessionStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
                1L,
                java.util.List.of(),
                java.util.Map.of(),
                java.util.List.of(),
                java.util.Map.of(),
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
    public Session appendMessage(String tenantId, String sessionId, SessionMessage message) {
        Objects.requireNonNull(message, "message");
        return sessionStore.update(new SessionKey(tenantId, sessionId), session -> {
            ArrayList<SessionMessage> messages = new ArrayList<>(session.messages());
            messages.add(message);
            return withMessages(session, messages);
        });
    }

    @Override
    public Session putState(String tenantId, String sessionId, String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return sessionStore.update(new SessionKey(tenantId, sessionId), session -> {
            HashMap<String, Object> state = new HashMap<>(session.state());
            state.put(key, value);
            return withState(session, state);
        });
    }

    @Override
    public Session removeState(String tenantId, String sessionId, String key) {
        Objects.requireNonNull(key, "key");
        return sessionStore.update(new SessionKey(tenantId, sessionId), session -> {
            HashMap<String, Object> state = new HashMap<>(session.state());
            state.remove(key);
            return withState(session, state);
        });
    }

    @Override
    public Session putMetadata(String tenantId, String sessionId, String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return sessionStore.update(new SessionKey(tenantId, sessionId), session -> {
            HashMap<String, Object> metadata = new HashMap<>(session.metadata());
            metadata.put(key, value);
            return withMetadata(session, metadata);
        });
    }

    @Override
    public Session removeMetadata(String tenantId, String sessionId, String key) {
        Objects.requireNonNull(key, "key");
        return sessionStore.update(new SessionKey(tenantId, sessionId), session -> {
            HashMap<String, Object> metadata = new HashMap<>(session.metadata());
            metadata.remove(key);
            return withMetadata(session, metadata);
        });
    }

    @Override
    public Session appendTask(String tenantId, String sessionId, Task task) {
        Objects.requireNonNull(task, "task");
        return sessionStore.update(new SessionKey(tenantId, sessionId), session -> {
            ArrayList<Task> tasks = new ArrayList<>(session.tasks());
            tasks.add(task);
            return withTasks(session, tasks);
        });
    }

    @Override
    public void delete(String tenantId, String sessionId) {
        sessionStore.remove(new SessionKey(tenantId, sessionId));
    }

    private Session touch(SessionKey key) {
        return sessionStore.update(key, session -> withTimestamps(session, session.updatedAt()));
    }

    private Session withMessages(Session session, java.util.List<SessionMessage> messages) {
        return copy(session, messages, session.state(), session.tasks(), session.metadata());
    }

    private Session withState(Session session, java.util.Map<String, Object> state) {
        return copy(session, session.messages(), state, session.tasks(), session.metadata());
    }

    private Session withMetadata(Session session, java.util.Map<String, Object> metadata) {
        return copy(session, session.messages(), session.state(), session.tasks(), metadata);
    }

    private Session withTasks(Session session, java.util.List<Task> tasks) {
        return copy(session, session.messages(), session.state(), tasks, session.metadata());
    }

    private Session copy(
            Session session,
            java.util.List<SessionMessage> messages,
            java.util.Map<String, Object> state,
            java.util.List<Task> tasks,
            java.util.Map<String, Object> metadata) {
        Instant now = clock.instant();
        return new Session(
                session.tenantId(),
                session.userId(),
                session.agentId(),
                session.sessionId(),
                session.version(),
                messages,
                state,
                tasks,
                metadata,
                session.createdAt(),
                now,
                now,
                expiresAt(now));
    }

    private Session withTimestamps(Session session, Instant updatedAt) {
        Instant now = clock.instant();
        return new Session(
                session.tenantId(),
                session.userId(),
                session.agentId(),
                session.sessionId(),
                session.version(),
                session.messages(),
                session.state(),
                session.tasks(),
                session.metadata(),
                session.createdAt(),
                updatedAt,
                now,
                expiresAt(now));
    }

    private Instant expiresAt(Instant now) {
        return ttl == null ? null : now.plus(ttl);
    }
}
