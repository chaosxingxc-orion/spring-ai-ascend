package com.huawei.ascend.service.access.model;

import java.util.Map;
import java.util.Objects;

public record EgressBinding(
        String tenantId,
        String sessionId,
        ReplyChannel replyChannel,
        String deliveryMode,
        String targetRef,
        String correlationId,
        Map<String, Object> attributes) {

    public EgressBinding {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(replyChannel, "replyChannel");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}


