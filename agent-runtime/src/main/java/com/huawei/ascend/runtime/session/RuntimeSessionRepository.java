package com.huawei.ascend.runtime.session;

import com.huawei.ascend.runtime.session.model.Session;
import com.huawei.ascend.runtime.session.model.SessionKey;

import java.util.Optional;

public interface RuntimeSessionRepository {
    Optional<Session> find(SessionKey key);

    Session save(Session session);

    void remove(SessionKey key);
}
