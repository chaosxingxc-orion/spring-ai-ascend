package com.huawei.ascend.service.session.store.memory;

import com.huawei.ascend.service.session.model.Session;
import com.huawei.ascend.service.session.model.SessionKey;
import com.huawei.ascend.service.session.store.SessionStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

public final class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<SessionKey, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<Session> find(SessionKey key) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(key, "key")));
    }

    @Override
    public Session save(Session session) {
        Objects.requireNonNull(session, "session");
        Session saved = withVersion(session, nextVersion(session.key()));
        sessions.put(saved.key(), saved);
        return saved;
    }

    @Override
    public Session update(SessionKey key, UnaryOperator<Session> mutator) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mutator, "mutator");
        return sessions.compute(key, (ignored, current) -> {
            if (current == null) {
                throw new SessionNotFoundException(key);
            }
            Session mutated = Objects.requireNonNull(mutator.apply(current), "mutated session");
            return withVersion(mutated, current.version() + 1);
        });
    }

    @Override
    public boolean saveIfVersion(Session session, long expectedVersion) {
        Objects.requireNonNull(session, "session");
        AtomicBoolean saved = new AtomicBoolean(false);
        sessions.compute(session.key(), (ignored, current) -> {
            if (current == null || current.version() != expectedVersion) {
                return current;
            }
            saved.set(true);
            return withVersion(session, expectedVersion + 1);
        });
        return saved.get();
    }

    @Override
    public void remove(SessionKey key) {
        sessions.remove(Objects.requireNonNull(key, "key"));
    }

    private long nextVersion(SessionKey key) {
        Session current = sessions.get(key);
        return current == null ? 1L : current.version() + 1;
    }

    private static Session withVersion(Session session, long version) {
        return new Session(
                session.tenantId(),
                session.userId(),
                session.agentId(),
                session.sessionId(),
                version,
                session.messages(),
                session.state(),
                session.tasks(),
                session.metadata(),
                session.createdAt(),
                session.updatedAt(),
                session.lastAccessedAt(),
                session.expiresAt());
    }
}
