package com.huawei.ascend.service.session.store;

import com.huawei.ascend.service.session.model.Session;
import com.huawei.ascend.service.session.model.SessionKey;

import java.util.Optional;
import java.util.function.UnaryOperator;

public interface SessionStore {
    Optional<Session> find(SessionKey key);

    Session save(Session session);

    Session update(SessionKey key, UnaryOperator<Session> mutator);

    boolean saveIfVersion(Session session, long expectedVersion);

    void remove(SessionKey key);
}
