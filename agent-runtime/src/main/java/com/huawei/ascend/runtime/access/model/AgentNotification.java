package com.huawei.ascend.runtime.access.model;

import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.common.RunStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AgentNotification(
        String tenantId,
        String sessionId,
        String taskId,
        NotificationType type,
        RunStatus status,
        List<Message> output,
        RunError error,
        Map<String, Object> metadata,
        boolean terminal) {

    public AgentNotification {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        output = output == null ? List.of() : List.copyOf(output);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public record RunError(String code, String message) {
        public RunError {
            code = (code == null || code.isBlank()) ? "UNKNOWN" : code;
            message = message == null ? "" : message;
        }
    }
}
