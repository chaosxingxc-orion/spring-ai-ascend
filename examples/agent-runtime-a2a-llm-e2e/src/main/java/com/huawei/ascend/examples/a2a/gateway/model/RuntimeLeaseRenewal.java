package com.huawei.ascend.examples.a2a.gateway.model;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record RuntimeLeaseRenewal(
        RuntimeInstanceId runtimeInstanceId,
        RuntimeState state,
        Duration ttl,
        SlaSnapshot slaSnapshot,
        Map<String, Object> metadata) {

    public RuntimeLeaseRenewal {
        runtimeInstanceId = Objects.requireNonNull(runtimeInstanceId, "runtimeInstanceId");
        state = Objects.requireNonNull(state, "state");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        slaSnapshot = slaSnapshot == null ? SlaSnapshot.empty() : slaSnapshot;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
