package com.huawei.ascend.agentsdk.spec.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.huawei.ascend.agentsdk.support.ValidationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentYamlLoaderTest {

    @Test
    void loadsYamlWithToolsAndSkillsResolvedRelativeToYamlFile() throws Exception {
        Path tempDir = testDirectory("loads");
        Path skill = Files.createDirectories(tempDir.resolve("skills").resolve("orders"));
        Files.writeString(skill.resolve("SKILL.md"), "# Order Skill\n");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: order-agent
                description: Order agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  provider: openai-compatible
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                prompt:
                  system: hello
                skills:
                  sources:
                    - ./skills/orders
                tools:
                  - name: queryOrder
                    description: Query an order
                    inputSchema:
                      type: object
                    ref:
                      type: file
                      class: example.OrderTools
                      method: query
                """);

        AgentSpec spec = new AgentYamlLoader().load(yaml);

        assertThat(spec.name()).isEqualTo("order-agent");
        assertThat(spec.displayName()).isEqualTo("order-agent");
        assertThat(spec.skillSpecs()).hasSize(1);
        assertThat(spec.skillSpecs().get(0).path()).isEqualTo(skill.toAbsolutePath().normalize());
        assertThat(spec.toolSpecs()).hasSize(1);
        assertThat(spec.toolSpecs().get(0).ref().scheme()).isEqualTo("file");
        assertThat(spec.toolSpecs().get(0).ref().attributes()).doesNotContainKey("path");
        assertThat(spec.toolSpecs().get(0).name()).isEqualTo("queryOrder");
    }

    @Test
    void stringShorthandRefsEmitTheKeysTheBuiltInResolversRead() throws Exception {
        Path tempDir = testDirectory("shorthand");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: shorthand-agent
                description: Shorthand agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                tools:
                  - name: javaTool
                    description: java
                    ref: "file:example.OrderTools#query"
                  - name: httpTool
                    description: http
                    ref: "http:https://api.example.com/orders"
                  - name: mcpTool
                    description: mcp
                    ref: "mcp:inventory/lookup"
                """);

        AgentSpec spec = new AgentYamlLoader().load(yaml);

        assertThat(spec.toolSpecs().get(0).ref().attributes())
                .containsEntry("class", "example.OrderTools")
                .containsEntry("method", "query");
        assertThat(spec.toolSpecs().get(1).ref().attributes())
                .containsEntry("url", "https://api.example.com/orders");
        assertThat(spec.toolSpecs().get(2).ref().attributes())
                .containsEntry("server", "inventory")
                .containsEntry("tool", "lookup");
    }

    @Test
    void malformedStringShorthandFailsAtLoadWithSyntaxHint() throws Exception {
        Path tempDir = testDirectory("shorthand-bad");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: bad-agent
                description: Bad agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                tools:
                  - name: javaTool
                    description: java
                    ref: "file:./tools/order.java"
                """);

        assertThatThrownBy(() -> new AgentYamlLoader().load(yaml))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("file:com.example.Class#method");
    }

    @Test
    void rejectsMissingEnvironmentVariable() throws Exception {
        Path tempDir = testDirectory("rejects-missing-env");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: broken-agent
                description: Broken agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  provider: openai-compatible
                  name: deepseek-chat
                  baseUrl: ${ASCEND_AGENT_SDK_TEST_MISSING}
                  apiKey: secret
                """);

        assertThatThrownBy(() -> new AgentYamlLoader().load(yaml))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ASCEND_AGENT_SDK_TEST_MISSING");
    }

    private static Path testDirectory(String name) throws Exception {
        return Files.createDirectories(Path.of("target", "agent-yaml-loader-test", name,
                UUID.randomUUID().toString()));
    }
}

