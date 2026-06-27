package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.mcp.McpAutoConfiguration;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenDeepAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenMcpToolAutoConfiguration;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenMcpToolInstaller;
import com.huawei.ascend.runtime.engine.spi.McpProvider;
import com.huawei.ascend.runtime.engine.spi.McpToolResult;
import com.huawei.ascend.runtime.engine.spi.McpToolSpec;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

class RuntimeMcpAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    McpAutoConfiguration.class,
                    OpenJiuwenMcpToolAutoConfiguration.class))
            .withPropertyValues("agent-runtime.mcp.servers[0].url=http://localhost:18081");

    @Test
    void mcpProviderStartsWithoutUserProvidedJackson2ObjectMapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class))
                .withPropertyValues("agent-runtime.mcp.servers[0].url=http://localhost:18081")
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(McpProvider.class);
                });
    }

    @Test
    void autoConfiguredMcpProviderWiresInstallerIntoDeepAgentHandlers() {
        contextRunner
                .withUserConfiguration(DeepAgentHandlerOnlyConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(McpProvider.class);
                    assertThat(context).hasSingleBean(OpenJiuwenMcpToolInstaller.class);
                    TestDeepAgentHandler handler = context.getBean(TestDeepAgentHandler.class);
                    assertThat(ReflectionTestUtils.getField(handler, "mcpToolInstaller"))
                            .isSameAs(context.getBean(OpenJiuwenMcpToolInstaller.class));
                });
    }

    @Test
    void mcpProviderWiresInstallerIntoDeepAgentHandlers() {
        contextRunner
                .withUserConfiguration(DeepAgentMcpConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenJiuwenMcpToolInstaller.class);
                    TestDeepAgentHandler handler = context.getBean(TestDeepAgentHandler.class);
                    handler.execute(executionContext()).toList();

                    assertThat(handler.agent.getRegisteredTools()).hasSize(1);
                    assertThat(handler.agent.getAgent().getAbilityManager().get("get_current_time")).isNotNull();
                });
    }

    private static AgentExecutionContext executionContext() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "deep-agent"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("time?")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1"));
    }

    @Configuration(proxyBeanMethods = false)
    static class DeepAgentHandlerOnlyConfiguration {
        @Bean
        TestDeepAgentHandler handler() {
            return new TestDeepAgentHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DeepAgentMcpConfiguration {
        @Bean
        McpProvider mcpProvider() {
            return new McpProvider() {
                @Override
                public List<McpToolSpec> listTools(AgentExecutionContext context) {
                    return List.of(new McpToolSpec(
                            "local-time",
                            "get_current_time",
                            "Current time",
                            "Return current time",
                            Map.of("type", "object", "properties", Map.of()),
                            Map.of(),
                            Map.of(),
                            Map.of(),
                            Map.of()));
                }

                @Override
                public McpToolResult callTool(AgentExecutionContext context, String serverId, String name,
                        Map<String, Object> arguments) {
                    return McpToolResult.success(List.of(), Map.of(), Map.of(), Map.of());
                }
            };
        }

        @Bean
        TestDeepAgentHandler handler() {
            return new TestDeepAgentHandler();
        }
    }

    static final class TestDeepAgentHandler extends OpenJiuwenDeepAgentRuntimeHandler {
        private RecordingDeepAgent agent;

        private TestDeepAgentHandler() {
            super("deep-agent");
        }

        @Override
        protected DeepAgent createOpenJiuwenDeepAgent(AgentExecutionContext context) {
            agent = new RecordingDeepAgent();
            return agent;
        }
    }

    private static final class RecordingDeepAgent extends DeepAgent {
        private RecordingDeepAgent() {
            super(
                    AgentCard.builder().id("deep-agent").name("deep-agent").description("test").build(),
                    DeepAgentConfig.builder().enableTaskLoop(true).build(),
                    Workspace.builder().rootPath("./target/deep-agent-mcp-autoconfig-test").build());
        }

        @Override
        public Iterator<Object> stream(
                Map<String, Object> inputs,
                AgentSessionApi session,
                List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }
}
