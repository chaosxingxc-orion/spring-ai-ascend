package com.huawei.ascend.runtime.engine.registry;

import com.huawei.ascend.runtime.engine.spi.AgentDriver;
import java.util.List;

/**
 * Holds the framework drivers the runtime can route to. Keyed by agent id ({@link
 * AgentDriver#name()}); also groupable by {@link AgentDriver#frameworkId()} for multi-framework
 * routing. The access layer resolves a driver here, wraps it in a
 * {@link com.huawei.ascend.runtime.engine.RunCoordinator} and streams the run.
 */
public interface AgentDriverRegistry {

    void register(AgentDriver driver);

    /** The driver serving the given agent id, or {@code null} if none. */
    AgentDriver find(String agentId);

    /** All drivers for a framework (e.g. {@code "openjiuwen"}). */
    List<AgentDriver> byFramework(String frameworkId);
}
