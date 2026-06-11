package com.huawei.ascend.agentsdk.factory;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentFactoryTest {

    @Test
    void convertsYamlToReactAgent() throws Exception {
        TestYaml yaml = exampleYaml("react");

        ReActAgent agent = AgentFactory.toReactAgent(yaml.path());

        assertThat(agent).isInstanceOf(ReActAgent.class);
        assertThat(agent.getCard().getId()).isEqualTo(yaml.agentId());
        assertRegisteredTools(yaml);
    }

    @Test
    void convertsYamlToDeepAgent() throws Exception {
        TestYaml yaml = exampleYaml("deepagent");

        DeepAgent agent = AgentFactory.toDeepAgent(yaml.path());

        assertThat(agent.getCard().getId()).isEqualTo(yaml.agentId());
        assertThat(agent.getConfig().getTools()).hasSize(2);
        assertThat(agent.getConfig().getSkillDirectories())
                .containsExactly(yaml.skillsRoot().toAbsolutePath().normalize().toString());
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
                    maxIterations: 3
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

        return new TestYaml(yaml, agentId, firstToolName, secondToolName, tempDir.resolve("skills"));
    }

    private static Path testDirectory(String agentType) throws Exception {
        return Files.createDirectories(Path.of("target", "agent-factory-test", agentType,
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

    private record TestYaml(Path path, String agentId, String firstToolName, String secondToolName, Path skillsRoot) {
    }
}
