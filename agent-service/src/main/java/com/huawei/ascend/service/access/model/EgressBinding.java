package com.huawei.ascend.service.access.model;

import java.util.Objects;

public record EgressBinding(
        String tenantId,
        String sessionId,
        String taskId,
        ReplyChannel replyChannel,
        String deliveryMode,
        String targetRef,
        String correlationId) {

    public EgressBinding {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(replyChannel, "replyChannel");
    }
}


