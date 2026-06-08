package com.huawei.ascend.runtime.access;

import com.huawei.ascend.runtime.common.Guards;
import java.util.Map;

public record AccessCancelCommand(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String taskId,
        String reason,
        Map<String, Object> metadata) {

    public AccessCancelCommand {
        tenantId = Guards.requireNonBlank(tenantId, "tenantId");
        userId = Guards.requireNonBlank(userId, "userId");
        agentId = Guards.requireNonBlank(agentId, "agentId");
        sessionId = Guards.requireNonBlank(sessionId, "sessionId");
        taskId = Guards.requireNonBlank(taskId, "taskId");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
