package com.huawei.ascend.agentsdk.factory;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.agentsdk.adapter.deepagent.OpenJiuwenDeepAgentHandlerAdapter;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentHandlerFactoryTest {

    @Test
    void exposesYamlToReactAgentThenReactAgentToHandlerSteps() throws Exception {
        TestYaml yaml = exampleYaml("react");

        ReActAgent agent = AgentHandlerFactory.toReactAgent(yaml.path());
        AgentRuntimeHandler handler = AgentHandlerFactory.toHandler(yaml.agentId(), agent);

        assertThat(agent).isInstanceOf(ReActAgent.class);
        assertThat(handler).isInstanceOf(OpenJiuwenAgentRuntimeHandler.class);
        assertThat(handler.agentId()).isEqualTo(yaml.agentId());
        assertRegisteredTools(yaml);
    }

    @Test
    void exposesYamlToDeepAgentThenDeepAgentToHandlerSteps() throws Exception {
        TestYaml yaml = exampleYaml("deepagent");

        Object agent = AgentHandlerFactory.toDeepAgent(yaml.path());
        AgentRuntimeHandler handler = AgentHandlerFactory.toHandler(yaml.agentId(), agent);

        assertThat(agent).isNotNull();
        assertThat(handler).isInstanceOf(OpenJiuwenDeepAgentHandlerAdapter.class);
        assertThat(handler.agentId()).isEqualTo(yaml.agentId());
    }

    @Test
    void fromYamlComposesReactAgentCreationAndHandlerWrapping() throws Exception {
        TestYaml yaml = exampleYaml("react");

        AgentRuntimeHandler handler = AgentHandlerFactory.fromYaml(yaml.path());

        assertThat(handler).isInstanceOf(OpenJiuwenAgentRuntimeHandler.class);
        assertThat(handler.agentId()).isEqualTo(yaml.agentId());
        assertRegisteredTools(yaml);
    }

    @Test
    void fromYamlComposesDeepAgentCreationAndHandlerWrapping() throws Exception {
        TestYaml yaml = exampleYaml("deepagent");

        AgentRuntimeHandler handler = AgentHandlerFactory.fromYaml(yaml.path());

        assertThat(handler).isInstanceOf(OpenJiuwenDeepAgentHandlerAdapter.class);
        assertThat(handler.agentId()).isEqualTo(yaml.agentId());
    }

    private TestYaml exampleYaml(String agentType) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String agentId = "sdkExampleAgent" + suffix;
        String firstToolName = "firstTool" + suffix;
        String secondToolName = "secondTool" + suffix;
        Path tempDir = testDirectory(agentType);
        Path common = Files.createDirectories(tempDir.resolve("skills").resolve("common"));
        Files.writeString(common.resolve("SKILL.md"), "# Common\n");
        Path order = Files.createDirectories(tempDir.resolve("skills").resolve("order"));
        Files.writeString(order.resolve("SKILL.md"), "# Order\n");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: %s
                description: SDK example agent
                framework:
                  type: openjiuwen
                  agent: %s
                  options:
                    executeMode: sdk-proof
                model:
                  provider: openai-compatible
                  name: deepseek-chat
                  baseUrl: https://api.deepseek.com
                  apiKey: sk-test
                prompt:
                  system: prove tools and skills
                skills:
                  sources:
                    - ./skills/common
                    - ./skills/order
                tools:
                  - name: %s
                    description: firstTool description
                    inputSchema:
                      type: object
                    ref:
                      type: file
                      class: %s
                      method: first
                  - name: %s
                    description: secondTool description
                    inputSchema:
                      type: object
                    ref:
                      type: file
                      class: %s
                      method: second
                """.formatted(
                agentId,
                agentType,
                firstToolName,
                TestTools.class.getName(),
                secondToolName,
                TestTools.class.getName()));

        return new TestYaml(yaml, agentId, firstToolName, secondToolName);
    }

    private static Path testDirectory(String agentType) throws Exception {
        return Files.createDirectories(Path.of("target", "agent-handler-factory-test", agentType,
                UUID.randomUUID().toString()));
    }

    private void assertRegisteredTools(TestYaml yaml) {
        Object first = Runner.resourceMgr().getTool(yaml.firstToolName());
        Object second = Runner.resourceMgr().getTool(yaml.secondToolName());
        assertThat(first).isInstanceOf(Tool.class);
        assertThat(second).isInstanceOf(Tool.class);
    }

    public static final class TestTools {
        private TestTools() {
        }

        public static Map<String, Object> first(Map<String, Object> inputs) {
            return Map.of("proof", "first", "inputs", inputs);
        }

        public static Map<String, Object> second(Map<String, Object> inputs) {
            return Map.of("proof", "second", "inputs", inputs);
        }
    }

    private record TestYaml(Path path, String agentId, String firstToolName, String secondToolName) {
    }
}

