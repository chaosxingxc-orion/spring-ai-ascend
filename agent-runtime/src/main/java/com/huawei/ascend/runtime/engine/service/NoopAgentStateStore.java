package com.huawei.ascend.runtime.engine.service;

import java.util.Optional;

/**
 * Agent state store used by legacy/manual dispatcher wiring that has not opted
 * into middleware state yet.
 */
public final class NoopAgentStateStore implements AgentStateStore {

    public static final NoopAgentStateStore INSTANCE = new NoopAgentStateStore();

    private NoopAgentStateStore() {
    }

    @Override
    public Optional<AgentStateSnapshot> load(AgentStateKey key) {
        return Optional.empty();
    }

    @Override
    public AgentStateSnapshot save(AgentStateSnapshot snapshot) {
        return snapshot;
    }

    @Override
    public void delete(AgentStateKey key) {
        // No persisted state to remove.
    }
}
