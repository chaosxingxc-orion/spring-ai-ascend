package com.huawei.ascend.runtime.engine.registry;

import com.huawei.ascend.runtime.engine.spi.AgentDriver;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link AgentDriverRegistry}: a concurrent map by agent id plus a per-framework view.
 * One per runtime instance (single-process); a distributed registry is a future concern.
 */
public final class DefaultAgentDriverRegistry implements AgentDriverRegistry {

    private final ConcurrentHashMap<String, AgentDriver> byAgentId = new ConcurrentHashMap<>();

    @Override
    public void register(AgentDriver driver) {
        byAgentId.put(driver.name(), driver);
    }

    @Override
    public AgentDriver find(String agentId) {
        return byAgentId.get(agentId);
    }

    @Override
    public List<AgentDriver> byFramework(String frameworkId) {
        List<AgentDriver> matches = new CopyOnWriteArrayList<>();
        for (AgentDriver driver : byAgentId.values()) {
            if (driver.frameworkId().equals(frameworkId)) {
                matches.add(driver);
            }
        }
        return matches;
    }
}
