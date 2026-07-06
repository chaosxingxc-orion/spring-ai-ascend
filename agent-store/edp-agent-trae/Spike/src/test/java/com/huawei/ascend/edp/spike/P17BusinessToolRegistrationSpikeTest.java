package com.huawei.ascend.edp.spike;

import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.config.EdpConfigLoader;
import com.huawei.ascend.edp.enhancer.EdpaAgentEnhancer;
import com.huawei.ascend.edp.tools.EdpaBusinessTools;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("spike")
@DisplayName("P17: EDPAgent 内置业务工具注册验证")
class P17BusinessToolRegistrationSpikeTest {

    @Test
    @DisplayName("P17-1: 构建 5 个 EDPAgent 业务工具")
    void buildFiveBusinessTools() {
        EdpConfig config = EdpConfigLoader.load(Path.of("src/main/resources/edp-config.yaml"));

        List<Tool> tools = EdpaBusinessTools.build(config);

        assertEquals(5, tools.size(), "Should build 5 EDPAgent business tools");
        assertToolNames(tools);
    }

    @Test
    @DisplayName("P17-2: LiteTodoWriteTool Schema 包含 edp-config.yaml 步骤 ID")
    void liteTodoSchemaIncludesConfiguredStepIds() {
        EdpConfig config = EdpConfigLoader.load(Path.of("src/main/resources/edp-config.yaml"));

        Tool liteTodo = EdpaBusinessTools.build(config).stream()
                .filter(tool -> EdpaAgentEnhancer.TOOL_LITE_TODO_WRITE.equals(tool.getCard().getName()))
                .findFirst()
                .orElseThrow();

        Map<String, Object> schema = liteTodo.getCard().getInputParams();
        String schemaText = schema.toString();
        assertTrue(schemaText.contains("todos"), "Schema must contain todos");
        assertTrue(schemaText.contains("step_id"), "Schema must contain step_id");
        assertTrue(schemaText.contains("1"), "Schema must contain configured step id 1");
        assertTrue(schemaText.contains("2"), "Schema must contain configured step id 2");
        assertTrue(schemaText.contains("3"), "Schema must contain configured step id 3");
    }

    @Test
    @DisplayName("P17-3: 业务工具可注册到 DeepAgent 能力列表")
    void enhanceRegistersBusinessToolsToDeepAgent() {
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder().build());
        EdpConfig config = EdpConfigLoader.load(Path.of("src/main/resources/edp-config.yaml"));

        assertDoesNotThrow(() -> EdpaAgentEnhancer.enhance(agent, config));

        List<String> names = agent.getAgent().getAbilityManager().listToolInfo().stream()
                .map(info -> info.getName())
                .toList();
        assertTrue(names.contains(EdpaAgentEnhancer.TOOL_LITE_TODO_WRITE));
        assertTrue(names.contains(EdpaAgentEnhancer.TOOL_CALL_MCP));
        assertTrue(names.contains(EdpaAgentEnhancer.TOOL_CALL_VERSATILE));
        assertTrue(names.contains(EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER));
        assertTrue(names.contains(EdpaAgentEnhancer.TOOL_CANCEL_TASK));
    }

    @Test
    @DisplayName("P17-4: 业务工具可直接调用，返回可观测状态")
    void businessToolsCanBeInvokedDirectly() throws Exception {
        EdpConfig config = EdpConfigLoader.load(Path.of("src/main/resources/edp-config.yaml"));
        List<Tool> tools = EdpaBusinessTools.build(config);

        Tool liteTodo = tools.stream()
                .filter(tool -> EdpaAgentEnhancer.TOOL_LITE_TODO_WRITE.equals(tool.getCard().getName()))
                .findFirst()
                .orElseThrow();

        Object result = liteTodo.invoke(Map.of("todos", List.of(Map.of("step_id", 1, "status", "done"))));

        assertNotNull(result);
        assertTrue(result.toString().contains("accepted"));
    }

    private void assertToolNames(List<Tool> tools) {
        List<String> names = tools.stream()
                .map(Tool::getCard)
                .map(ToolCard::getName)
                .toList();
        assertEquals(List.of(
                EdpaAgentEnhancer.TOOL_LITE_TODO_WRITE,
                EdpaAgentEnhancer.TOOL_CALL_MCP,
                EdpaAgentEnhancer.TOOL_CALL_VERSATILE,
                EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER,
                EdpaAgentEnhancer.TOOL_CANCEL_TASK), names);
    }
}
