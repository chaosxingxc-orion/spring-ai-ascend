package com.huawei.ascend.examples.a2a.gateway.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AgentInteractionEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String tenantId,
        String sourceRuntimeId,
        String sourceAgentId,
        String targetRuntimeId,
        String targetAgentId,
        String sessionId,
        String taskId,
        String correlationId,
        String traceId,
        String grantId,
        String a2aMethod,
        String status,
        long routeResolveMs,
        long firstByteMs,
        long totalMs,
        long requestBytes,
        long responseBytes,
        String errorCode,
        String payloadHash,
        String payloadRef,
        Map<String, Object> metadata) {

    public AgentInteractionEvent {
        eventId = required(eventId, "eventId");
        eventType = required(eventType, "eventType");
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        tenantId = required(tenantId, "tenantId");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
