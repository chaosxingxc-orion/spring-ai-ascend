package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the neutral {@link AgentCardDescriptor} record: default values,
 * null-coalescing in the compact constructor, and wither-style builders.
 */
class AgentCardDescriptorTest {

    @Test
    void ofSetsRequiredFieldsAndDefaults() {
        AgentCardDescriptor d = AgentCardDescriptor.of("my-agent", "A test agent");

        assertThat(d.name()).isEqualTo("my-agent");
        assertThat(d.description()).isEqualTo("A test agent");
        assertThat(d.version()).isEqualTo("0.1.0");
        assertThat(d.endpoint()).isEqualTo("/a2a");
        assertThat(d.providerOrganization()).isEqualTo("spring-ai-ascend");
        assertThat(d.providerUrl()).isEqualTo("");
    }

    @Test
    void ofSetsDefaultCapabilities() {
        AgentCardDescriptor d = AgentCardDescriptor.of("agent", "desc");

        // Intentional fail-safe change (#229/#230): capabilities default to false.
        // The boot configuration overrides these from the registered handler's metadata.
        assertThat(d.capabilities()).isNotNull();
        assertThat(d.capabilities().streaming()).isFalse();
        assertThat(d.capabilities().pushNotifications()).isFalse();
        assertThat(d.capabilities().extendedAgentCard()).isFalse();
    }

    @Test
    void ofSetsDefaultModes() {
        AgentCardDescriptor d = AgentCardDescriptor.of("agent", "desc");

        // Intentional fail-safe change (#233): defaultOutputModes defaults to ["text"].
        // Handlers that emit artifacts override defaultOutputModes() to add "artifact".
        assertThat(d.defaultInputModes()).containsExactly("text");
        assertThat(d.defaultOutputModes()).containsExactly("text");
    }

    @Test
    void ofHasEmptyCollections() {
        AgentCardDescriptor d = AgentCardDescriptor.of("agent", "desc");

        assertThat(d.skills()).isEmpty();
        assertThat(d.securitySchemes()).isEmpty();
        assertThat(d.securityRequirements()).isEmpty();
        assertThat(d.additionalInterfaces()).isEmpty();
        assertThat(d.signatures()).isEmpty();
    }

    @Test
    void compactConstructorNullCoercesCollections() {
        AgentCardDescriptor d = new AgentCardDescriptor(
                "x", "y", null, null, null, null, null, null, null,
                AgentCapabilitiesDescriptor.defaults(),
                null, null, null, null, null, null, null);

        assertThat(d.defaultInputModes()).isEmpty();
        assertThat(d.defaultOutputModes()).isEmpty();
        assertThat(d.skills()).isEmpty();
        assertThat(d.securitySchemes()).isEmpty();
        assertThat(d.securityRequirements()).isEmpty();
        assertThat(d.additionalInterfaces()).isEmpty();
        assertThat(d.signatures()).isEmpty();
    }

    @Test
    void withVersionReturnsCopyWithUpdatedVersion() {
        AgentCardDescriptor original = AgentCardDescriptor.of("agent", "desc");
        AgentCardDescriptor updated = original.withVersion("2.0.0");

        assertThat(updated.version()).isEqualTo("2.0.0");
        assertThat(updated.name()).isEqualTo("agent");
        assertThat(original.version()).isEqualTo("0.1.0");
    }

    @Test
    void withEndpointReturnsCopyWithUpdatedEndpoint() {
        AgentCardDescriptor original = AgentCardDescriptor.of("agent", "desc");
        AgentCardDescriptor updated = original.withEndpoint("/custom-a2a");

        assertThat(updated.endpoint()).isEqualTo("/custom-a2a");
        assertThat(original.endpoint()).isEqualTo("/a2a");
    }

    @Test
    void withProviderReturnsCopyWithUpdatedProvider() {
        AgentCardDescriptor d = AgentCardDescriptor.of("agent", "desc")
                .withProvider("acme-corp", "https://acme.example.com");

        assertThat(d.providerOrganization()).isEqualTo("acme-corp");
        assertThat(d.providerUrl()).isEqualTo("https://acme.example.com");
    }

    @Test
    void withCapabilitiesReturnsCopyWithUpdatedCapabilities() {
        AgentCapabilitiesDescriptor none = AgentCapabilitiesDescriptor.none();
        AgentCardDescriptor d = AgentCardDescriptor.of("agent", "desc").withCapabilities(none);

        assertThat(d.capabilities().streaming()).isFalse();
        assertThat(d.capabilities().pushNotifications()).isFalse();
    }

    @Test
    void agentCapabilitiesDescriptorDefaultsAreFalseSafe() {
        AgentCapabilitiesDescriptor caps = AgentCapabilitiesDescriptor.defaults();

        // Intentional fail-safe change (#229/#230): defaults are false; handlers opt in.
        assertThat(caps.streaming()).isFalse();
        assertThat(caps.pushNotifications()).isFalse();
        assertThat(caps.extendedAgentCard()).isFalse();
    }

    @Test
    void agentCapabilitiesDescriptorNoneAlsoAllFalse() {
        AgentCapabilitiesDescriptor none = AgentCapabilitiesDescriptor.none();

        assertThat(none.streaming()).isFalse();
        assertThat(none.pushNotifications()).isFalse();
        assertThat(none.extendedAgentCard()).isFalse();
    }

    @Test
    void agentCapabilitiesDescriptorCanBeExplicitlyEnabled() {
        AgentCapabilitiesDescriptor caps = new AgentCapabilitiesDescriptor(true, true, false);

        assertThat(caps.streaming()).isTrue();
        assertThat(caps.pushNotifications()).isTrue();
        assertThat(caps.extendedAgentCard()).isFalse();
    }

    @Test
    void agentSkillDescriptorOfSetsMinimalFields() {
        AgentSkillDescriptor skill = AgentSkillDescriptor.of("s1", "Skill One", "Does things");

        assertThat(skill.id()).isEqualTo("s1");
        assertThat(skill.name()).isEqualTo("Skill One");
        assertThat(skill.description()).isEqualTo("Does things");
        assertThat(skill.tags()).isEmpty();
        assertThat(skill.examples()).isEmpty();
        assertThat(skill.inputModes()).isEmpty();
        assertThat(skill.outputModes()).isEmpty();
    }

    @Test
    void agentInterfaceDescriptorOfSetsBinding() {
        AgentInterfaceDescriptor iface = AgentInterfaceDescriptor.of("JSONRPC", "/a2a");

        assertThat(iface.protocolBinding()).isEqualTo("JSONRPC");
        assertThat(iface.path()).isEqualTo("/a2a");
        assertThat(iface.protocolVersion()).isNull();
    }

    @Test
    void securitySchemeDescriptorApiKeyFactory() {
        SecuritySchemeDescriptor s = SecuritySchemeDescriptor.apiKey("header", "X-Api-Key", "API key auth");

        assertThat(s.type()).isEqualTo("apiKey");
        assertThat(s.location()).isEqualTo("header");
        assertThat(s.name()).isEqualTo("X-Api-Key");
        assertThat(s.description()).isEqualTo("API key auth");
    }

    @Test
    void securitySchemeDescriptorHttpFactory() {
        SecuritySchemeDescriptor s = SecuritySchemeDescriptor.http("bearer", "Bearer token");

        assertThat(s.type()).isEqualTo("http");
        assertThat(s.scheme()).isEqualTo("bearer");
    }
}
