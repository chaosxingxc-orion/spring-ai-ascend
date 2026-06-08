package com.huawei.ascend.runtime.engine;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;

/**
 * Holds the {@code AgentRuntimeHandler} instances known to the engine, keyed by agent
 * id. See engine model design §8.2.
 */
public interface AgentRuntimeHandlerRegistry {

    void register(String agentId, AgentRuntimeHandler handler);

    /**
     * @throws IllegalStateException if no handler is registered for the id.
     */
    AgentRuntimeHandler findByAgentId(String agentId);
}
