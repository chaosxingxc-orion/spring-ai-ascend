package com.huawei.ascend.service.session.store.memory;

import com.huawei.ascend.service.session.model.Session;
import com.huawei.ascend.service.session.model.SessionKey;
import com.huawei.ascend.service.session.store.SessionStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<SessionKey, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<Session> find(SessionKey key) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(key, "key")));
    }

    @Override
    public Session save(Session session) {
        Objects.requireNonNull(session, "session");
        sessions.put(session.key(), session);
        return session;
    }

    @Override
    public void remove(SessionKey key) {
        sessions.remove(Objects.requireNonNull(key, "key"));
    }
}
