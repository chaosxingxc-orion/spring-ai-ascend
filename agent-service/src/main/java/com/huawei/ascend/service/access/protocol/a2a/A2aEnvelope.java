package com.huawei.ascend.service.access.protocol.a2a;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record A2aEnvelope(
        A2aContext context,
        A2aMessage message) {

    public A2aEnvelope {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(message, "message");
    }

    public record A2aContext(
            String tenantId,
            String userId,
            String agentId,
            String sessionId,
            String contextId,
            String idempotencyKey,
            String correlationId) {

        public A2aContext {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(agentId, "agentId");
        }
    }

    public record A2aMessage(
            String text,
            List<Object> parts,
            Map<String, Object> metadata) {

        public A2aMessage {
            parts = parts == null ? List.of() : List.copyOf(parts);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

}


