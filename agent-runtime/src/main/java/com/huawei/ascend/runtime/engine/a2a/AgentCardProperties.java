package com.huawei.ascend.runtime.engine.a2a;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

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

    /**
     * Organization URL in the card's provider block. Defaults to blank, which the
     * discovery controller rewrites to the published base (public-base-url or
     * request-derived) at serve time, so the default card never leaks a
     * hardcoded host.
     */
    private String organizationUrl;

    /** A2A endpoint path. Defaults to {@code "/a2a"}. */
    private String endpoint;

    /** Default card-level input modes. Defaults to {@code ["text"]}. */
    private List<String> defaultInputModes;

    /** Default card-level output modes. Defaults to {@code ["text", "artifact"]}. */
    private List<String> defaultOutputModes;

    /** Agent capability overrides. Unset flags preserve the default card shape. */
    private CapabilitiesProperties capabilities;

    /** Published A2A skills. Defaults to an empty list. */
    private List<SkillProperties> skills;

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

    public List<String> getDefaultInputModes() { return defaultInputModes; }
    public void setDefaultInputModes(List<String> defaultInputModes) {
        this.defaultInputModes = defaultInputModes;
    }

    public List<String> getDefaultOutputModes() { return defaultOutputModes; }
    public void setDefaultOutputModes(List<String> defaultOutputModes) {
        this.defaultOutputModes = defaultOutputModes;
    }

    public CapabilitiesProperties getCapabilities() { return capabilities; }
    public void setCapabilities(CapabilitiesProperties capabilities) {
        this.capabilities = capabilities;
    }

    public List<SkillProperties> getSkills() { return skills; }
    public void setSkills(List<SkillProperties> skills) { this.skills = skills; }

    /** Returns {@code true} when the user explicitly configured a card name. */
    public boolean hasExplicitName() {
        return name != null && !name.isBlank();
    }

    /**
     * Build an {@link AgentCard} using this properties object's values for
     * any set fields, with sensible defaults for unset fields. Delegates to
     * {@link AgentCards} for the canonical card shape — a second inline copy
     * here meant every card fix had to land twice.
     *
     * @param name the card name (non-blank; supplied by the caller)
     */
    public AgentCard createAgentCard(String name) {
        AgentCard card = AgentCards.create(name,
                blankToDefault(description, "agent-runtime"),
                blankToDefault(version, "0.1.0"),
                blankToDefault(endpoint, "/a2a"));
        AgentCard.Builder builder = AgentCard.builder(card);

        if (capabilities != null) {
            builder.capabilities(toAgentCapabilities(card.capabilities(), capabilities));
        }
        if (hasItems(defaultInputModes)) {
            builder.defaultInputModes(defaultInputModes);
        }
        if (hasItems(defaultOutputModes)) {
            builder.defaultOutputModes(defaultOutputModes);
        }
        if (skills != null) {
            builder.skills(skills.stream()
                    .map(AgentCardProperties::toAgentSkill)
                    .toList());
        }

        boolean hasOrganization = organization != null && !organization.isBlank();
        boolean hasOrganizationUrl = organizationUrl != null && !organizationUrl.isBlank();
        if (hasOrganization || hasOrganizationUrl) {
            builder.provider(new AgentProvider(
                    blankToDefault(organization, "spring-ai-ascend"),
                    blankToDefault(organizationUrl, "")));
        }
        return builder.build();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static boolean hasItems(List<String> values) {
        return values != null && !values.isEmpty();
    }

    private static AgentCapabilities toAgentCapabilities(
            AgentCapabilities defaults, CapabilitiesProperties configured) {
        return AgentCapabilities.builder()
                .streaming(configured.streaming != null ? configured.streaming : defaults.streaming())
                .pushNotifications(configured.pushNotifications != null
                        ? configured.pushNotifications : defaults.pushNotifications())
                .extendedAgentCard(configured.extendedAgentCard != null
                        ? configured.extendedAgentCard : defaults.extendedAgentCard())
                .build();
    }

    private static AgentSkill toAgentSkill(SkillProperties skill) {
        return AgentSkill.builder()
                .id(skill.id)
                .name(skill.name)
                .description(skill.description)
                .tags(skill.tags != null ? skill.tags : List.of())
                .examples(skill.examples)
                .inputModes(skill.inputModes)
                .outputModes(skill.outputModes)
                .build();
    }

    public static class CapabilitiesProperties {
        private Boolean streaming;
        private Boolean pushNotifications;
        private Boolean extendedAgentCard;

        public Boolean getStreaming() { return streaming; }
        public void setStreaming(Boolean streaming) { this.streaming = streaming; }

        public Boolean getPushNotifications() { return pushNotifications; }
        public void setPushNotifications(Boolean pushNotifications) {
            this.pushNotifications = pushNotifications;
        }

        public Boolean getExtendedAgentCard() { return extendedAgentCard; }
        public void setExtendedAgentCard(Boolean extendedAgentCard) {
            this.extendedAgentCard = extendedAgentCard;
        }
    }

    public static class SkillProperties {
        private String id;
        private String name;
        private String description;
        private List<String> tags;
        private List<String> examples;
        private List<String> inputModes;
        private List<String> outputModes;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }

        public List<String> getExamples() { return examples; }
        public void setExamples(List<String> examples) { this.examples = examples; }

        public List<String> getInputModes() { return inputModes; }
        public void setInputModes(List<String> inputModes) { this.inputModes = inputModes; }

        public List<String> getOutputModes() { return outputModes; }
        public void setOutputModes(List<String> outputModes) { this.outputModes = outputModes; }
    }
}
