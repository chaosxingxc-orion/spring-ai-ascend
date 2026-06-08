package com.huawei.ascend.runtime.common;

import java.util.Map;
import java.util.Objects;

public record AgentResponseEvent(
        String requestId,
        long sequence,
        ResponseType responseType,
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String taskId,
        ResponseStatus status,
        String output,
        ErrorInfo error,
        Map<String, Object> metadata) {

    public AgentResponseEvent {
        requestId = requireNonBlank(requestId, "requestId");
        if (sequence < 1L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        responseType = Objects.requireNonNull(responseType, "responseType");
        tenantId = requireNonBlank(tenantId, "tenantId");
        agentId = requireNonBlank(agentId, "agentId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        taskId = requireNonBlank(taskId, "taskId");
        status = Objects.requireNonNull(status, "status");
        output = output == null ? "" : output;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentResponseEvent taskAccepted(
            String requestId, String tenantId, String userId, String agentId, String sessionId, String taskId) {
        return new AgentResponseEvent(requestId, 1L, ResponseType.TASK, tenantId, userId, agentId,
                sessionId, taskId, ResponseStatus.ACCEPTED, "", null, Map.of());
    }

    public static AgentResponseEvent deltaText(
            String requestId, long sequence, String tenantId, String userId,
            String agentId, String sessionId, String taskId, String output) {
        return new AgentResponseEvent(requestId, sequence, ResponseType.DELTA, tenantId, userId, agentId,
                sessionId, taskId, ResponseStatus.RUNNING, output, null, Map.of());
    }

    public static AgentResponseEvent finalText(
            String requestId, String tenantId, String userId, String agentId, String sessionId,
            String taskId, String output, Map<String, Object> metadata) {
        return new AgentResponseEvent(requestId, 1L, ResponseType.FINAL, tenantId, userId, agentId,
                sessionId, taskId, ResponseStatus.COMPLETED, output, null, metadata);
    }

    public static AgentResponseEvent error(
            String requestId, String tenantId, String userId, String agentId, String sessionId,
            String taskId, ErrorInfo error) {
        return new AgentResponseEvent(requestId, 1L, ResponseType.ERROR, tenantId, userId, agentId,
                sessionId, taskId, ResponseStatus.FAILED, "", Objects.requireNonNull(error, "error"), Map.of());
    }

    public boolean terminal() {
        return responseType == ResponseType.FINAL || responseType == ResponseType.ERROR
                || status == ResponseStatus.CANCELLED || status == ResponseStatus.INPUT_REQUIRED;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
