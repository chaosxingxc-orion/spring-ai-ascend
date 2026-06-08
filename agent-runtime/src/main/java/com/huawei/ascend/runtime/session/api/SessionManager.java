package com.huawei.ascend.runtime.session.api;

import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.session.Session;

import java.util.List;
import java.util.Optional;

public interface SessionManager {
    default Session loadOrCreate(String tenantId, String userId, String agentId, String sessionId) {
        return loadOrCreate(tenantId, userId, agentId, sessionId, List.of());
    }

    Session loadOrCreate(
            String tenantId,
            String userId,
            String agentId,
            String sessionId,
            List<Message> currentUserInput);

    Optional<Session> get(String tenantId, String sessionId);

    boolean exists(String tenantId, String sessionId);

    void delete(String tenantId, String sessionId);
}
