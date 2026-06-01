package com.huawei.ascend.service.access.protocol.async;

import com.huawei.ascend.service.access.model.AccessOperation;
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
            AccessOperation operation,
            String idempotencyKey,
            String correlationId,
            String replyTopic) {

        public AsyncHeaders {
            operation = operation == null ? AccessOperation.SUBMIT : operation;
        }
    }

    public record AsyncBody(String query, Object payload) {
    }
}
