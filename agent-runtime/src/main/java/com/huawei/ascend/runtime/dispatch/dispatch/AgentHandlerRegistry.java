package com.huawei.ascend.runtime.dispatch.dispatch;

import com.huawei.ascend.runtime.dispatch.spi.AgentHandler;

/**
 * Holds the {@code AgentHandler} instances known to the engine, keyed by agent
 * id. See engine model design §8.2.
 */
public interface AgentHandlerRegistry {

    void register(String agentId, AgentHandler handler);

    /**
     * @throws IllegalStateException if no handler is registered for the id.
     */
    AgentHandler findByAgentId(String agentId);
}
