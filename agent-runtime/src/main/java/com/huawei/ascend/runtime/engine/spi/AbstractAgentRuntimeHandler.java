package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.common.Guards;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.a2aproject.sdk.spec.AgentCard;

/**
 * Base class for one business Agent hosted by one runtime instance.
 *
 * <p>Subclasses are normal Spring beans. The runtime auto-registers them as
 * {@link AgentRuntimeHandler} implementations and exposes their A2A {@link AgentCard}
 * at {@code /.well-known/agent-card.json}. This keeps the business integration
 * shape explicit: implement one runtime Agent handler, get one A2A Agent Card.
 */
public abstract class AbstractAgentRuntimeHandler implements AgentRuntimeHandler, AgentCardProvider {

    private final String agentId;
    private final String name;
    private final String description;
    private final String version;
    private final String endpoint;
    private final List<AgentRuntimeProvider> providers = new ArrayList<>();

    protected AbstractAgentRuntimeHandler(String agentId, String name, String description) {
        this(agentId, name, description, "0.1.0", "/a2a");
    }

    protected AbstractAgentRuntimeHandler(
            String agentId, String name, String description, String version, String endpoint) {
        this.agentId = Guards.requireNonBlank(agentId, "agentId");
        this.name = Guards.requireNonBlank(name, "name");
        this.description = Guards.requireNonBlank(description, "description");
        this.version = Guards.requireNonBlank(version, "version");
        this.endpoint = Guards.requireNonBlank(endpoint, "endpoint");
    }

    @Override
    public final String agentId() {
        return agentId;
    }

    @Override
    public final AgentCard agentCard() {
        return AgentCards.create(name, description, version, endpoint);
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
