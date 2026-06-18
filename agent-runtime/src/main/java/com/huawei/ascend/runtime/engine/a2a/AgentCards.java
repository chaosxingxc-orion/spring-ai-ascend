package com.huawei.ascend.runtime.engine.a2a;

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
        org.springframework.util.Assert.hasText(name, "name must not be blank");
        org.springframework.util.Assert.hasText(description, "description must not be blank");
        org.springframework.util.Assert.hasText(version, "version must not be blank");
        org.springframework.util.Assert.hasText(endpoint, "endpoint must not be blank");
        AgentCapabilities capabilities = AgentCapabilities.builder()
                .streaming(true)
                .pushNotifications(true)
                .extendedAgentCard(false)
                .build();
        return AgentCard.builder()
                .name(name)
                .description(description)
                .version(version)
                // Blank provider URL: the discovery controller rewrites it to the
                // published base (public-base-url or request-derived) at serve time,
                // so the default card never leaks a hardcoded host.
                .provider(new AgentProvider("spring-ai-ascend", ""))
                .capabilities(capabilities)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text", "artifact"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), endpoint)))
                .build();
    }
}
