package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AgentCardPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesBindingConfiguration.class);

    @Test
    void yamlCardFieldsOverrideDefaultCapabilitiesModesAndSkills() {
        AgentCardProperties properties = new AgentCardProperties();
        properties.setDescription("Configured card");
        properties.setVersion("2.0.0");
        properties.setDefaultOutputModes(List.of("text"));

        AgentCardProperties.CapabilitiesProperties capabilities =
                new AgentCardProperties.CapabilitiesProperties();
        capabilities.setStreaming(false);
        capabilities.setPushNotifications(false);
        properties.setCapabilities(capabilities);

        AgentCardProperties.SkillProperties skill = new AgentCardProperties.SkillProperties();
        skill.setId("issue-315-demo-skill");
        skill.setName("Issue 315 Demo Skill");
        skill.setDescription("Configured through YAML properties.");
        skill.setTags(List.of("issue-315", "yaml-card"));
        skill.setExamples(List.of("Show the configured Agent Card skill."));
        skill.setInputModes(List.of("text"));
        skill.setOutputModes(List.of("text"));
        properties.setSkills(List.of(skill));

        AgentCard card = properties.createAgentCard("configured-agent");

        assertThat(card.description()).isEqualTo("Configured card");
        assertThat(card.version()).isEqualTo("2.0.0");
        assertThat(card.capabilities().streaming()).isFalse();
        assertThat(card.capabilities().pushNotifications()).isFalse();
        assertThat(card.capabilities().extendedAgentCard()).isFalse();
        assertThat(card.defaultInputModes()).containsExactly("text");
        assertThat(card.defaultOutputModes()).containsExactly("text");
        assertThat(card.skills()).hasSize(1);
        assertThat(card.skills().get(0).id()).isEqualTo("issue-315-demo-skill");
        assertThat(card.skills().get(0).name()).isEqualTo("Issue 315 Demo Skill");
        assertThat(card.skills().get(0).description()).isEqualTo("Configured through YAML properties.");
        assertThat(card.skills().get(0).tags()).containsExactly("issue-315", "yaml-card");
        assertThat(card.skills().get(0).examples()).containsExactly("Show the configured Agent Card skill.");
        assertThat(card.skills().get(0).inputModes()).containsExactly("text");
        assertThat(card.skills().get(0).outputModes()).containsExactly("text");
    }

    @Test
    void defaultCardShapeIsPreservedWhenYamlFieldsAreUnset() {
        AgentCard card = new AgentCardProperties().createAgentCard("default-agent");

        assertThat(card.capabilities().streaming()).isTrue();
        assertThat(card.capabilities().pushNotifications()).isTrue();
        assertThat(card.capabilities().extendedAgentCard()).isFalse();
        assertThat(card.defaultInputModes()).containsExactly("text");
        assertThat(card.defaultOutputModes()).containsExactly("text", "artifact");
        assertThat(card.skills()).isEmpty();
    }

    @Test
    void springBootBindsKebabCaseYamlProperties() {
        contextRunner
                .withPropertyValues(
                        "agent-runtime.access.a2a.agent-card.name=bound-agent",
                        "agent-runtime.access.a2a.agent-card.capabilities.streaming=false",
                        "agent-runtime.access.a2a.agent-card.capabilities.push-notifications=false",
                        "agent-runtime.access.a2a.agent-card.default-output-modes[0]=text",
                        "agent-runtime.access.a2a.agent-card.skills[0].id=bound-skill",
                        "agent-runtime.access.a2a.agent-card.skills[0].name=Bound Skill",
                        "agent-runtime.access.a2a.agent-card.skills[0].description=Bound from properties",
                        "agent-runtime.access.a2a.agent-card.skills[0].tags[0]=bound",
                        "agent-runtime.access.a2a.agent-card.skills[0].examples[0]=Use the bound skill",
                        "agent-runtime.access.a2a.agent-card.skills[0].input-modes[0]=text",
                        "agent-runtime.access.a2a.agent-card.skills[0].output-modes[0]=text")
                .run(context -> {
                    AgentCardProperties properties = context.getBean(AgentCardProperties.class);

                    AgentCard card = properties.createAgentCard(properties.getName());

                    assertThat(card.capabilities().streaming()).isFalse();
                    assertThat(card.capabilities().pushNotifications()).isFalse();
                    assertThat(card.defaultOutputModes()).containsExactly("text");
                    assertThat(card.skills()).hasSize(1);
                    assertThat(card.skills().get(0).id()).isEqualTo("bound-skill");
                    assertThat(card.skills().get(0).inputModes()).containsExactly("text");
                    assertThat(card.skills().get(0).outputModes()).containsExactly("text");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentCardProperties.class)
    static class PropertiesBindingConfiguration {
    }
}
