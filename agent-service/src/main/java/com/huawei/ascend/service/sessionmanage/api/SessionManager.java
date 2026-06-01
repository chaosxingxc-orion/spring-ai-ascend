package com.huawei.ascend.service.sessionmanage.api;

import com.huawei.ascend.service.sessionmanage.model.Session;
import com.huawei.ascend.service.sessionmanage.model.SessionMessage;
import com.huawei.ascend.service.sessionmanage.model.Task;

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

    Session appendTask(String tenantId, String sessionId, Task task);

    void delete(String tenantId, String sessionId);
}
