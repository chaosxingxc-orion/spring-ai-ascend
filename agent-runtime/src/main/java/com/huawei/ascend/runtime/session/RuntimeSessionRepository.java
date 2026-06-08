package com.huawei.ascend.runtime.session;


import java.util.Optional;

public interface RuntimeSessionRepository {
    Optional<Session> find(SessionKey key);

    Session save(Session session);

    void remove(SessionKey key);
}
