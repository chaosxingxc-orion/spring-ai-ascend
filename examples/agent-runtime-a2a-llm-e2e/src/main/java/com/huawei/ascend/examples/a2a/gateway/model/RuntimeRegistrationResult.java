package com.huawei.ascend.examples.a2a.gateway.model;

import java.time.Instant;

public record RuntimeRegistrationResult(
        RuntimeInstanceId runtimeInstanceId,
        String tenantId,
        String agentId,
        RuntimeState state,
        Instant expiresAt) {
}
