package com.huawei.ascend.service.session.api;

import com.huawei.ascend.service.session.model.Session;

import java.util.Optional;

public interface SessionManager {
    Session loadOrCreate(String tenantId, String userId, String agentId, String sessionId);

    Optional<Session> get(String tenantId, String sessionId);

    boolean exists(String tenantId, String sessionId);

    void delete(String tenantId, String sessionId);
}
