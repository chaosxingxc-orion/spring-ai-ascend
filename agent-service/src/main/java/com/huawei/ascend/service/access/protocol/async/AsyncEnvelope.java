package com.huawei.ascend.service.access.protocol.async;

import java.util.Objects;

public record AsyncEnvelope(AsyncHeaders headers, AsyncBody body) {
    public AsyncEnvelope {
        headers = Objects.requireNonNull(headers, "headers");
        body = Objects.requireNonNull(body, "body");
    }

    public record AsyncHeaders(
            String tenantId,
            String userId,
            String agentId,
            String sessionId,
            String idempotencyKey,
            String correlationId,
            String replyTopic) {
    }

    public record AsyncBody(String query, Object payload) {
    }
}
