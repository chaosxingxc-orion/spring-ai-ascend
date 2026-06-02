package com.huawei.ascend.service.session.api;

import com.huawei.ascend.service.session.model.Session;
import com.huawei.ascend.service.session.model.SessionMessage;

import java.util.Optional;

public interface SessionManager {
    Session loadOrCreate(String tenantId, String userId, String agentId, String sessionId);

    Optional<Session> get(String tenantId, String sessionId);

    boolean exists(String tenantId, String sessionId);

    Session appendMessage(String tenantId, String sessionId, SessionMessage message);

    Session putState(String tenantId, String sessionId, String key, Object value);

    Session removeState(String tenantId, String sessionId, String key);

    Session putMetadata(String tenantId, String sessionId, String key, Object value);

    Session removeMetadata(String tenantId, String sessionId, String key);

    void delete(String tenantId, String sessionId);
}
