package com.huawei.ascend.service.session.store;

import com.huawei.ascend.service.session.model.Session;
import com.huawei.ascend.service.session.model.SessionKey;

import java.util.Optional;

public interface SessionStore {
    Optional<Session> find(SessionKey key);

    Session save(Session session);

    void remove(SessionKey key);
}
