package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.common.Guards;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Convenience base class for one business Agent hosted by one runtime instance.
 *
 * <p>Subclasses are normal Spring beans. The runtime auto-registers them as
 * {@link AgentRuntimeHandler} implementations. A2A Agent Card metadata remains
 * a separate optional capability supplied by {@link AgentCardProvider}, so
 * execution behavior and public protocol metadata can evolve independently.
 */
public abstract class AbstractAgentRuntimeHandler implements AgentRuntimeHandler {

    private final String agentId;
    private final List<AgentRuntimeProvider> providers = new ArrayList<>();

    protected AbstractAgentRuntimeHandler(String agentId) {
        this.agentId = Guards.requireNonBlank(agentId, "agentId");
    }

    @Override
    public final String agentId() {
        return agentId;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public final List<AgentRuntimeProvider> providers() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * Adds an optional capability to this handler.
     *
     * <p>Subclasses should call this from their constructor. This keeps future
     * features composable instead of forcing every capability into a new base
     * class.
     */
    protected final void addRuntimeProvider(AgentRuntimeProvider provider) {
        providers.add(Objects.requireNonNull(provider, "provider"));
    }
}
