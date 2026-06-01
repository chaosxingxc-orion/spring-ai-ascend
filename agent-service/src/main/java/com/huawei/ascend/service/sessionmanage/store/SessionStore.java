package com.huawei.ascend.service.sessionmanage.store;

import com.huawei.ascend.service.sessionmanage.model.Session;
import com.huawei.ascend.service.sessionmanage.model.SessionKey;

import java.util.Optional;
import java.util.function.UnaryOperator;

public interface SessionStore {
    Optional<Session> find(SessionKey key);

    Session save(Session session);

    Session update(SessionKey key, UnaryOperator<Session> mutator);

    boolean saveIfVersion(Session session, long expectedVersion);

    void remove(SessionKey key);
}
