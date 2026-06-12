package com.huawei.ascend.runtime.engine.a2a;

import java.util.List;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A2A agent-card metadata configurable via {@code application.yaml}.
 *
 * <p>All fields are optional. When {@code name} is left unset, the runtime
 * falls back to the handler-derived default card (as before). Set {@code name}
 * to opt into explicit YAML-driven card control; every unset field then draws
 * a sensible production-safe default.
 *
 * <p>Example:
 * <pre>{@code
 * agent-runtime:
 *   access:
 *     a2a:
 *       agent-card:
 *         name: my-agent
 *         description: My custom agent served by agent-runtime.
 *         version: "1.0"
 * }</pre>
 */
@ConfigurationProperties("agent-runtime.access.a2a.agent-card")
public class AgentCardProperties {

    /** Agent name shown in {@code /.well-known/agent-card.json}. Falls back to handler {@code agentId}. */
    private String name;

    /** Human-readable description. Defaults to {@code "agent-runtime"}. */
    private String description;

    /** Agent version string. Defaults to {@code "0.1.0"}. */
    private String version;

    /** Organization name in the card's provider block. Defaults to {@code "spring-ai-ascend"}. */
    private String organization;

    /** Organization URL in the card's provider block. Defaults to {@code "http://localhost:8080"}. */
    private String organizationUrl;

    /** A2A endpoint path. Defaults to {@code "/a2a"}. */
    private String endpoint;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getOrganizationUrl() { return organizationUrl; }
    public void setOrganizationUrl(String organizationUrl) { this.organizationUrl = organizationUrl; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    /** Returns {@code true} when the user explicitly configured a card name. */
    public boolean hasExplicitName() {
        return name != null && !name.isBlank();
    }

    /**
     * Build an {@link AgentCard} using this properties object's values for
     * any set fields, with sensible defaults for unset fields.
     *
     * @param name the card name (non-blank; supplied by the caller)
     */
    public AgentCard createAgentCard(String name) {
        String resolvedDescription = blankToDefault(description, "agent-runtime");
        String resolvedVersion = blankToDefault(version, "0.1.0");
        String resolvedEndpoint = blankToDefault(endpoint, "/a2a");
        String resolvedOrg = blankToDefault(organization, "spring-ai-ascend");
        String resolvedOrgUrl = blankToDefault(organizationUrl, "http://localhost:8080");

        AgentCapabilities capabilities = AgentCapabilities.builder()
                .streaming(true)
                .pushNotifications(true)
                .extendedAgentCard(false)
                .build();
        return AgentCard.builder()
                .name(name)
                .description(resolvedDescription)
                .url(resolvedEndpoint)
                .version(resolvedVersion)
                .provider(new AgentProvider(resolvedOrg, resolvedOrgUrl))
                .capabilities(capabilities)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text", "artifact"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), resolvedEndpoint)))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
