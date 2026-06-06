package com.huawei.ascend.runtime.engine;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link AgentRuntimeHandlerRegistry} backed by a concurrent map.
 */
public class DefaultAgentRuntimeHandlerRegistry implements AgentRuntimeHandlerRegistry {

    private final Map<String, AgentRuntimeHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public void register(String agentId, AgentRuntimeHandler handler) {
        String key = requireNonBlank(agentId, "agentId");
        AgentRuntimeHandler previous = handlers.putIfAbsent(key, Objects.requireNonNull(handler, "handler"));
        if (previous != null) {
            throw new IllegalStateException("AgentRuntimeHandler already registered for agentId=" + key);
        }
    }

    @Override
    public AgentRuntimeHandler findByAgentId(String agentId) {
        AgentRuntimeHandler handler = handlers.get(agentId);
        if (handler == null) {
            throw new IllegalStateException("No AgentRuntimeHandler registered for agentId=" + agentId);
        }
        return handler;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
