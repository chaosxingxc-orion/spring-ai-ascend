package com.huawei.ascend.examples.a2a.gateway.model;

import java.time.Instant;

public record RuntimeLeaseResult(
        RuntimeInstanceId runtimeInstanceId,
        RuntimeState state,
        Instant expiresAt) {
}
