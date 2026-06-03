package com.huawei.ascend.runtime.access.protocol.a2a.model;

public record A2aTaskQueryParams(
        String tenantId,
        String sessionId,
        String taskId) {
}
