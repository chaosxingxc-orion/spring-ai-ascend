package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenSkillHubAutoConfiguration;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenSkillHubInstaller;
import com.huawei.ascend.runtime.engine.spi.SkillDefinition;
import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import com.huawei.ascend.runtime.engine.spi.SkillSummary;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RuntimeSkillHubAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenJiuwenSkillHubAutoConfiguration.class));

    @Test
    void skillHubProviderWiresInstallerIntoOpenJiuwenHandlers() {
        contextRunner
                .withUserConfiguration(SkillHubHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenJiuwenSkillHubInstaller.class);
                    TestOpenJiuwenHandler handler = context.getBean(TestOpenJiuwenHandler.class);
                    handler.execute(executionContext()).toList();
                    assertThat(handler.agent.registeredSkills).containsExactly("skills/hotel");
                });
    }

    @Test
    void noProviderLeavesAutoConfigurationInactive() {
        contextRunner
                .withUserConfiguration(HandlerOnlyConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(OpenJiuwenSkillHubInstaller.class));
    }

    private static AgentExecutionContext executionContext() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "agent-a"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("book hotel")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1"));
    }

    @Configuration(proxyBeanMethods = false)
    static class SkillHubHandlerConfiguration {
        @Bean
        SkillHubProvider skillHubProvider() {
            return new SkillHubProvider() {
                @Override
                public List<SkillSummary> listSkills(AgentExecutionContext context) {
                    return List.of(new SkillSummary("hotel", "Hotel", "Hotel booking", List.of(), Map.of()));
                }

                @Override
                public SkillDefinition loadSkill(AgentExecutionContext context, String skillId) {
                    return new SkillDefinition(
                            skillId,
                            "Hotel",
                            "Hotel booking",
                            "Use hotel workflow.",
                            List.of(),
                            List.of(),
                            Map.of(OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH, "skills/hotel"));
                }
            };
        }

        @Bean
        TestOpenJiuwenHandler handler() {
            return new TestOpenJiuwenHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HandlerOnlyConfiguration {
        @Bean
        TestOpenJiuwenHandler handler() {
            return new TestOpenJiuwenHandler();
        }
    }

    static final class TestOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
        private final RecordingAgent agent = new RecordingAgent();

        private TestOpenJiuwenHandler() {
            super("agent-a");
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            return agent;
        }

        @Override
        protected Iterator<Object> runOpenJiuwenAgentStreaming(BaseAgent agent, Object input, String conversationId,
                List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }

    private static final class RecordingAgent extends BaseAgent {
        private final List<Object> registeredSkills = new ArrayList<>();

        private RecordingAgent() {
            super(AgentCard.builder().id("agent-a").name("agent-a").description("test").build());
        }

        @Override
        public void registerSkill(Object skills) {
            registeredSkills.add(skills);
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object input, Session session) {
            return null;
        }

        @Override
        public Iterator<Object> stream(Object input, Session session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }
}
