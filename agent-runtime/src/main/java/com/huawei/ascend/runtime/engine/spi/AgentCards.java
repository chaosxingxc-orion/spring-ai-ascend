package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.common.Guards;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Small factory for the default A2A card shape used by local runtime handlers.
 */
public final class AgentCards {

    private AgentCards() {
    }

    public static AgentCard create(String name, String description) {
        return create(name, description, "0.1.0", "/a2a");
    }

    public static AgentCard create(String name, String description, String version, String endpoint) {
        Guards.requireNonBlank(name, "name");
        Guards.requireNonBlank(description, "description");
        Guards.requireNonBlank(version, "version");
        Guards.requireNonBlank(endpoint, "endpoint");
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
}
