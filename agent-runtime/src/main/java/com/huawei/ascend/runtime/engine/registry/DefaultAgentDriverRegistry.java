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
        if (driver == null) {
            throw new IllegalArgumentException("AgentDriver must not be null");
        }
        String agentId = driver.name();
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException(
                    "AgentDriver.name() must not be blank: " + driver.getClass().getName());
        }
        if (driver.frameworkId() == null || driver.frameworkId().isBlank()) {
            throw new IllegalArgumentException(
                    "AgentDriver.frameworkId() must not be blank for agentId=" + agentId);
        }
        AgentDriver existing = byAgentId.putIfAbsent(agentId, driver);
        if (existing != null && existing != driver) {
            throw new IllegalStateException("duplicate AgentDriver agentId=" + agentId
                    + " (existing=" + existing.getClass().getName()
                    + ", new=" + driver.getClass().getName() + ")");
        }
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
