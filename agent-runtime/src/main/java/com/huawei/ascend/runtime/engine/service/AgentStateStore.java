package com.huawei.ascend.runtime.engine.service;

import java.util.Map;
import java.util.Optional;

/**
 * Runtime-owned Agent state store.
 *
 * <p>This is an API consumed by the engine dispatcher and embedders. It is not
 * an Agent framework SPI: framework adapters read/write state through
 * {@code AgentExecutionContext}, while the store implementation can later move
 * from memory to Redis, JDBC, or another durable backend.
 */
public interface AgentStateStore {

    Optional<Map<String, Object>> load(String key);

    Map<String, Object> save(String key, Map<String, Object> state);

    void delete(String key);
}
