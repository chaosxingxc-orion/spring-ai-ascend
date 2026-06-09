package com.huawei.ascend.runtime.engine.service;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * W1 in-process Agent state store.
 *
 * <p>The implementation is intentionally small and dependency-free. It gives
 * SDK users a working checkpoint store while preserving the same
 * {@link AgentStateStore} contract for future distributed backends.
 */
public class InMemoryAgentStateStore implements AgentStateStore {

    private final ConcurrentMap<AgentStateKey, AgentStateSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Optional<AgentStateSnapshot> load(AgentStateKey key) {
        return Optional.ofNullable(snapshots.get(Objects.requireNonNull(key, "key")));
    }

    @Override
    public AgentStateSnapshot save(AgentStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        snapshots.put(snapshot.key(), snapshot);
        return snapshot;
    }

    @Override
    public void delete(AgentStateKey key) {
        snapshots.remove(Objects.requireNonNull(key, "key"));
    }
}
