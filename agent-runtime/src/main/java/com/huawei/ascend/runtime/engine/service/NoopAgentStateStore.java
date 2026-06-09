package com.huawei.ascend.runtime.engine.service;

import java.util.Map;
import java.util.Optional;

/**
 * Compatibility-only duplicate Agent state store for legacy/manual dispatcher
 * wiring.
 *
 * <p>Spring runtime wiring uses {@link InMemoryAgentStateStore}, or a
 * user-provided {@link AgentStateStore} bean. This no-op implementation is kept
 * only so older tests and SDK code that still call the two-argument
 * {@code EngineDispatcher} constructor do not need to opt into state storage.
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
