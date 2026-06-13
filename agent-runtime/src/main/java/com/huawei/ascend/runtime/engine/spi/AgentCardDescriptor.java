package com.huawei.ascend.runtime.engine.spi;

import java.util.List;
import java.util.Map;

/**
 * Protocol-neutral description of an agent's public card metadata.
 *
 * <p>This record is the sole currency between {@link AgentCardProvider} and the
 * A2A protocol bridge ({@code engine.a2a.A2aAgentCardMapper}). It carries zero
 * {@code org.a2aproject} imports, which keeps {@code engine.spi} ArchUnit-clean.
 *
 * <p>The compact constructor null-coalesces all collection fields to empty so
 * callers never have to guard against {@code null} list/map returns.
 *
 * <p>Use {@link #of(String, String)} as the entry-point and chain the
 * {@code with*} builders for optional fields.
 */
public record AgentCardDescriptor(
        String name,
        String description,
        String version,
        String endpoint,
        String providerOrganization,
        String providerUrl,
        String documentationUrl,
        String iconUrl,
        String protocolVersion,
        AgentCapabilitiesDescriptor capabilities,
        List<String> defaultInputModes,
        List<String> defaultOutputModes,
        List<AgentSkillDescriptor> skills,
        Map<String, SecuritySchemeDescriptor> securitySchemes,
        List<Map<String, List<String>>> securityRequirements,
        List<AgentInterfaceDescriptor> additionalInterfaces,
        List<SignatureDescriptor> signatures) {

    public AgentCardDescriptor {
        defaultInputModes = defaultInputModes != null ? List.copyOf(defaultInputModes) : List.of();
        defaultOutputModes = defaultOutputModes != null ? List.copyOf(defaultOutputModes) : List.of();
        skills = skills != null ? List.copyOf(skills) : List.of();
        securitySchemes = securitySchemes != null ? Map.copyOf(securitySchemes) : Map.of();
        securityRequirements = securityRequirements != null ? List.copyOf(securityRequirements) : List.of();
        additionalInterfaces = additionalInterfaces != null ? List.copyOf(additionalInterfaces) : List.of();
        signatures = signatures != null ? List.copyOf(signatures) : List.of();
    }

    /**
     * Minimal entry-point: name and description are required; every other field
     * gets its fail-safe default (version {@code "0.1.0"}, endpoint {@code "/a2a"},
     * provider {@code "spring-ai-ascend"}, capabilities streaming=false/push=false,
     * outputModes=["text"]).
     *
     * <p>The boot configuration overrides capabilities and modes from the registered
     * handler's declared metadata, so the descriptor default is intentionally conservative.
     */
    public static AgentCardDescriptor of(String name, String description) {
        return new AgentCardDescriptor(
                name,
                description,
                "0.1.0",
                "/a2a",
                "spring-ai-ascend",
                "",
                null,
                null,
                null,
                AgentCapabilitiesDescriptor.defaults(),
                List.of("text"),
                List.of("text"),
                null,
                null,
                null,
                null,
                null);
    }

    /** Returns a copy with the given version. */
    public AgentCardDescriptor withVersion(String version) {
        return new AgentCardDescriptor(name, description, version, endpoint, providerOrganization, providerUrl,
                documentationUrl, iconUrl, protocolVersion, capabilities, defaultInputModes, defaultOutputModes,
                skills, securitySchemes, securityRequirements, additionalInterfaces, signatures);
    }

    /** Returns a copy with the given endpoint path. */
    public AgentCardDescriptor withEndpoint(String endpoint) {
        return new AgentCardDescriptor(name, description, version, endpoint, providerOrganization, providerUrl,
                documentationUrl, iconUrl, protocolVersion, capabilities, defaultInputModes, defaultOutputModes,
                skills, securitySchemes, securityRequirements, additionalInterfaces, signatures);
    }

    /** Returns a copy with the given provider organization and URL. */
    public AgentCardDescriptor withProvider(String organization, String url) {
        return new AgentCardDescriptor(name, description, version, endpoint, organization, url,
                documentationUrl, iconUrl, protocolVersion, capabilities, defaultInputModes, defaultOutputModes,
                skills, securitySchemes, securityRequirements, additionalInterfaces, signatures);
    }

    /** Returns a copy with the given capabilities. */
    public AgentCardDescriptor withCapabilities(AgentCapabilitiesDescriptor capabilities) {
        return new AgentCardDescriptor(name, description, version, endpoint, providerOrganization, providerUrl,
                documentationUrl, iconUrl, protocolVersion, capabilities, defaultInputModes, defaultOutputModes,
                skills, securitySchemes, securityRequirements, additionalInterfaces, signatures);
    }

    /** Returns a copy with the given default output modes. */
    public AgentCardDescriptor withDefaultOutputModes(List<String> outputModes) {
        return new AgentCardDescriptor(name, description, version, endpoint, providerOrganization, providerUrl,
                documentationUrl, iconUrl, protocolVersion, capabilities, defaultInputModes, outputModes,
                skills, securitySchemes, securityRequirements, additionalInterfaces, signatures);
    }

    /** Returns a copy with the given skills. */
    public AgentCardDescriptor withSkills(List<AgentSkillDescriptor> skills) {
        return new AgentCardDescriptor(name, description, version, endpoint, providerOrganization, providerUrl,
                documentationUrl, iconUrl, protocolVersion, capabilities, defaultInputModes, defaultOutputModes,
                skills, securitySchemes, securityRequirements, additionalInterfaces, signatures);
    }
}
