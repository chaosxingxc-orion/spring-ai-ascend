package com.huawei.ascend.edp.rail;

import com.huawei.ascend.edp.channel.ToolDataChannel;
import com.huawei.ascend.edp.channel.ToolDataKey;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * McpInterruptRail 工具数据通道单元测试。
 */
class McpInterruptRailToolDataTest {

    @Test
    void testAfterToolCall_StoresResultKeyAndCompatibleKeys() {
        ToolDataChannel channel = new ToolDataChannel();
        McpInterruptRail rail = new McpInterruptRail(null, channel);
        AgentCallbackContext ctx = buildContext(Map.of(
                "result_key", "fund_recommend_result",
                "products", List.of(Map.of("name", "稳健理财A")),
                "versatile_query", "购买第一支理财产品",
                "history_info", List.of("P001"),
                "history_params", Map.of("risk", "R2")));

        rail.afterToolCall(ctx);

        ToolDataKey key = new ToolDataKey("defaultTenant", "edp-agent", "session-1", "session-1");
        Map<String, Object> stored = channel.get(key, "fund_recommend_result");
        assertNotNull(stored);
        assertEquals(1, ((List<?>) stored.get("products")).size());
        assertFalse(stored.containsKey("result_key"));
        assertFalse(stored.containsKey("versatile_query"));
        assertNotNull(channel.get(key, "mcp_products_data"));
        assertEquals("购买第一支理财产品",
                channel.get(key, "mcp_to_versatile_information").get("query_description"));
        assertEquals(List.of("P001"), channel.get(key, "history_info").get("value"));
        assertEquals("R2", channel.get(key, "history_params").get("risk"));
    }

    @Test
    void testAfterToolCall_DefaultKeyWhenResultKeyMissing() {
        ToolDataChannel channel = new ToolDataChannel();
        McpInterruptRail rail = new McpInterruptRail(null, channel);
        AgentCallbackContext ctx = buildContext(Map.of("products", List.of(Map.of("name", "稳健理财A"))));

        rail.afterToolCall(ctx);

        ToolDataKey key = new ToolDataKey("defaultTenant", "edp-agent", "session-1", "session-1");
        assertNotNull(channel.get(key, "mcp_products_data"));
    }

    @Test
    void testAfterToolCall_NonMcpIgnored() {
        ToolDataChannel channel = new ToolDataChannel();
        McpInterruptRail rail = new McpInterruptRail(null, channel);
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName("call_versatile")
                .toolResult(Map.of("result_key", "fund_recommend_result"))
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .session(new AgentSessionApi("session-1"))
                .inputs(inputs)
                .build();

        rail.afterToolCall(ctx);

        ToolDataKey key = new ToolDataKey("defaultTenant", "edp-agent", "session-1", "session-1");
        assertNull(channel.get(key, "fund_recommend_result"));
    }

    @Test
    void testBeforeToolCall_ExecutesLocalScriptAndStoresResult() throws IOException {
        Path skillsDir = Files.createTempDirectory("mcp-skills");
        Path skillScriptDir = skillsDir.resolve("demo_skill").resolve("scripts");
        Files.createDirectories(skillScriptDir);
        Files.writeString(skillScriptDir.resolve("run_mcp.py"), """
                import argparse
                import json
                import os
                parser = argparse.ArgumentParser()
                parser.add_argument('--arguments-json')
                args = parser.parse_args()
                data = json.loads(args.arguments_json or os.environ.get('SKILL_INPUT', '{}'))
                print(json.dumps({
                    'products': [{'name': '稳健理财A'}],
                    'total': 1,
                    'versatile_query': '购买第一支理财产品',
                    'history_params': data.get('mcp_params', {}),
                    'history_info': ['P001']
                }, ensure_ascii=False))
                """, StandardCharsets.UTF_8);

        ToolDataChannel channel = new ToolDataChannel();
        McpInterruptRail rail = new McpInterruptRail(null, channel, skillsDir);
        ToolCall toolCall = new ToolCall();
        toolCall.setId("tool-call-1");
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName("call_mcp")
                .toolCall(toolCall)
                .toolArgs(Map.of(
                        "script_command", "python demo_skill/scripts/run_mcp.py",
                        "script_params", Map.of("mcp_params", Map.of("risk", "R2"))))
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .session(new AgentSessionApi("session-1"))
                .inputs(inputs)
                .build();

        rail.beforeToolCall(ctx);
        rail.afterToolCall(ctx);

        ToolDataKey key = new ToolDataKey("defaultTenant", "edp-agent", "session-1", "session-1");
        Map<String, Object> stored = channel.get(key, "mcp_products_data");
        assertNotNull(stored);
        assertEquals(1, stored.get("total"));
        assertEquals("购买第一支理财产品",
                channel.get(key, "mcp_to_versatile_information").get("query_description"));
        assertEquals("R2", channel.get(key, "history_params").get("risk"));
    }

    private AgentCallbackContext buildContext(Map<String, Object> result) {
        ToolCall toolCall = new ToolCall();
        toolCall.setId("tool-call-1");
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName("call_mcp")
                .toolCall(toolCall)
                .toolResult(result)
                .toolMsg(ToolMessage.builder().toolCallId("tool-call-1").content(result).build())
                .build();
        return AgentCallbackContext.builder()
                .session(new AgentSessionApi("session-1"))
                .inputs(inputs)
                .build();
    }
}
