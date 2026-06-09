package com.huawei.ascend.runtime.engine.service;

import java.util.Map;
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
    public Optional<Map<String, Object>> load(String key) {
        return Optional.empty();
    }

    @Override
    public Map<String, Object> save(String key, Map<String, Object> state) {
        return state;
    }

    @Override
    public void delete(String key) {
        // No persisted state to remove.
    }
}
