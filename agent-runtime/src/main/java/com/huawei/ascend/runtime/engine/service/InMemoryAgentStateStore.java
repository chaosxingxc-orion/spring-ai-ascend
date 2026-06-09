package com.huawei.ascend.runtime.engine.service;

import com.huawei.ascend.runtime.common.Guards;
import java.util.Map;
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

    private final ConcurrentMap<String, Map<String, Object>> states = new ConcurrentHashMap<>();

    @Override
    public Optional<Map<String, Object>> load(String key) {
        return Optional.ofNullable(states.get(Guards.requireNonBlank(key, "key")));
    }

    @Override
    public Map<String, Object> save(String key, Map<String, Object> state) {
        Guards.requireNonBlank(key, "key");
        Map<String, Object> copy = Map.copyOf(Objects.requireNonNull(state, "state"));
        states.put(key, copy);
        return copy;
    }

    @Override
    public void delete(String key) {
        states.remove(Guards.requireNonBlank(key, "key"));
    }
}
