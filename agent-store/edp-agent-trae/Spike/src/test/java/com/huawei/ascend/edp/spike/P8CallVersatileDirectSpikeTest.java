package com.huawei.ascend.edp.spike;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.edp.config.EdpAgentConfig;
import com.huawei.ascend.edp.config.EdpAgentConfigLoader;
import com.huawei.ascend.edp.enhancer.EdpaAgentEnhancer;
import com.huawei.ascend.edp.rail.VersatileInterruptRail;
import com.huawei.ascend.edp.tools.EdpaBusinessTools;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.legacy.TaskSession;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("spike")
@DisplayName("P8: call_versatile 直接调用 Versatile 服务验证")
class P8CallVersatileDirectSpikeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("P8-1: call_versatile schema 支持 Skill 最小参数")
    void callVersatileSchemaSupportsMinimalSkillArgs() {
        Tool tool = callVersatileTool();

        Map<String, Object> schema = tool.getCard().getInputParams();
        String schemaText = schema.toString();

        assertEquals(EdpaBusinessTools.TOOL_CALL_VERSATILE, tool.getCard().getName());
        assertTrue(schemaText.contains("query_description"));
        assertTrue(schemaText.contains("query_intent"));
        assertTrue(schemaText.contains("required=[query_description, query_intent]"));
    }

    @Test
    @DisplayName("P8-2: CallVersatileTool 保持 thin tool，只返回 delegate_intent")
    void callVersatileToolStaysThin() throws Exception {
        Tool tool = callVersatileTool();
        Map<String, Object> args = Map.of(
                "query_description", "推荐理财产品",
                "query_intent", "理财推荐");

        Object result = tool.invoke(args);
        Map<?, ?> resultMap = assertInstanceOf(Map.class, result);
        Map<?, ?> input = assertInstanceOf(Map.class, resultMap.get("input"));

        assertEquals(EdpaBusinessTools.TOOL_CALL_VERSATILE, resultMap.get("tool"));
        assertEquals("delegate_intent", resultMap.get("status"));
        assertEquals("推荐理财产品", input.get("query_description"));
        assertEquals("理财推荐", input.get("query_intent"));
        assertFalse(resultMap.containsKey("source"));
        assertFalse(resultMap.containsKey("content"));
    }

    @Test
    @DisplayName("P8-3: edp-agent.yaml 可解析 versatile 配置")
    void edpAgentYamlParsesVersatileConfig() {
        EdpAgentConfig config = EdpAgentConfigLoader.load(Path.of("src/main/resources/edp-agent.yaml"));
        EdpAgentConfig.Versatile versatile = config.getVersatile();

        assertNotNull(versatile);
        assertEquals("http://localhost:8095/v1/0/agent-manager/workflows/{workflow_id}/conversations/{conversation_id}",
                versatile.getUrl());
        assertEquals("30s", versatile.getTimeout());
        assertEquals("mock_workflow", versatile.getUrlVariables().get("workflow_id"));
        assertEquals("controller", versatile.getQueryParams().get("type"));
        assertEquals("10", versatile.getQueryParams().get("workspace_id"));
        assertEquals("true", versatile.getHeaders().get("stream"));
    }

    @Test
    @DisplayName("P8-4: 非 call_versatile 工具不被 VersatileInterruptRail 拦截")
    void nonVersatileToolPassesThrough() {
        EdpAgentConfig config = EdpAgentConfigLoader.load(Path.of("src/main/resources/edp-agent.yaml"));
        VersatileInterruptRail rail = new VersatileInterruptRail(null, config.getVersatile());
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName(EdpaAgentEnhancer.TOOL_LITE_TODO_WRITE)
                .toolArgs(Map.of("todos", List.of()))
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().inputs(inputs).build();

        rail.beforeToolCall(ctx);

        assertFalse(ctx.getExtra().containsKey("_skip_tool"));
        assertNull(inputs.getToolResult());
        assertNull(inputs.getToolMsg());
    }

    @Test
    @DisplayName("P8-5: 真实调用 localhost:8095，toolResult.content 非空且为标准 JSON")
    void directCallLocalVersatileReturnsStandardJsonContent() throws Exception {
        EdpAgentConfig config = EdpAgentConfigLoader.load(Path.of("src/main/resources/edp-agent.yaml"));
        VersatileInterruptRail rail = new VersatileInterruptRail(null, config.getVersatile());
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName(EdpaBusinessTools.TOOL_CALL_VERSATILE)
                .toolCall(ToolCall.builder()
                        .id("va-call-001")
                        .name(EdpaBusinessTools.TOOL_CALL_VERSATILE)
                        .arguments("{\"query_description\":\"推荐理财产品\",\"query_intent\":\"理财推荐\"}")
                        .build())
                .toolArgs(Map.of())
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .inputs(inputs)
                .session(new TaskSession("p8-direct-local"))
                .build();

        rail.beforeToolCall(ctx);

        assertEquals(Boolean.TRUE, ctx.getExtra().get("_skip_tool"));
        Map<?, ?> toolResult = assertInstanceOf(Map.class, inputs.getToolResult());
        String toolResultJson = OBJECT_MAPPER.writeValueAsString(toolResult);
        System.out.println("[P8_TEST_REPORT] P8-5 | call_versatile toolResult=" + toolResultJson);
        assertEquals("versatile", toolResult.get("source"));
        assertEquals("completed", toolResult.get("status"));
        String content = assertInstanceOf(String.class, toolResult.get("content"));
        System.out.println("[P8_TEST_REPORT] P8-5 | call_versatile toolResult.content=" + content);
        assertFalse(content.isBlank());
        JsonNode json = OBJECT_MAPPER.readTree(content);
        assertTrue(json.isObject() || json.isArray());
        assertNotNull(inputs.getToolMsg());
        assertEquals("va-call-001", inputs.getToolMsg().getToolCallId());
    }

    private Tool callVersatileTool() {
        return EdpaBusinessTools.build(null).stream()
                .filter(tool -> EdpaBusinessTools.TOOL_CALL_VERSATILE.equals(tool.getCard().getName()))
                .findFirst()
                .orElseThrow();
    }
}
