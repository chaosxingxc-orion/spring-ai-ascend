package com.huawei.ascend.runtime.engine.spi;

import java.util.List;
import java.util.Objects;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Base class for one business Agent hosted by one runtime instance.
 *
 * <p>Subclasses are normal Spring beans. The runtime auto-registers them as
 * {@link AgentRuntimeHandler} implementations and exposes their A2A {@link AgentCard}
 * at {@code /.well-known/agent-card.json}. This keeps the business integration
 * shape explicit: implement one runtime Agent handler, get one A2A Agent Card.
 */
public abstract class AbstractAgentRuntimeHandler implements AgentRuntimeHandler {

    private final String agentId;
    private final String name;
    private final String description;
    private final String version;
    private final String endpoint;

    protected AbstractAgentRuntimeHandler(String agentId, String name, String description) {
        this(agentId, name, description, "0.1.0", "/a2a");
    }

    protected AbstractAgentRuntimeHandler(
            String agentId, String name, String description, String version, String endpoint) {
        this.agentId = requireText(agentId, "agentId");
        this.name = requireText(name, "name");
        this.description = requireText(description, "description");
        this.version = requireText(version, "version");
        this.endpoint = requireText(endpoint, "endpoint");
    }

    @Override
    public final String agentId() {
        return agentId;
    }

    public final AgentCard agentCard() {
        AgentCapabilities capabilities = AgentCapabilities.builder()
                .streaming(true)
                .pushNotifications(true)
                .extendedAgentCard(false)
                .build();
        return AgentCard.builder()
                .name(name)
                .description(description)
                .url(endpoint)
                .version(version)
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:8080"))
                .capabilities(capabilities)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text", "artifact"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), endpoint)))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
