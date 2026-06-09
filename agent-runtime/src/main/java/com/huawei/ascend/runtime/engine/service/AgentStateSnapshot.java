package com.huawei.ascend.runtime.engine.service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable checkpoint payload exposed to an {@code AgentRuntimeHandler}.
 *
 * <p>The runtime stores only framework-neutral key/value state here. Business
 * systems should keep domain state in their own repositories and store a
 * reference in this snapshot when needed.
 */
public record AgentStateSnapshot(
        AgentStateKey key,
        long revision,
        Map<String, Object> values,
        Instant updatedAt) {

    public AgentStateSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        values = Map.copyOf(values);
    }

    public static AgentStateSnapshot empty(AgentStateKey key) {
        return new AgentStateSnapshot(key, 0, Map.of(), Instant.now());
    }

    public AgentStateSnapshot next(Map<String, Object> nextValues) {
        return new AgentStateSnapshot(key, revision + 1, nextValues, Instant.now());
    }
}
