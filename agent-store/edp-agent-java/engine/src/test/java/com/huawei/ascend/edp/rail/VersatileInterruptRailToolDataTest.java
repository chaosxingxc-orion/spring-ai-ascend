package com.huawei.ascend.edp.rail;

import com.huawei.ascend.edp.channel.ToolDataChannel;
import com.huawei.ascend.edp.channel.ToolDataKey;
import com.huawei.ascend.edp.config.EdpaSpringBootConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VersatileInterruptRail 工具数据通道单元测试。
 */
class VersatileInterruptRailToolDataTest {

    @Test
    void testBuildInputs_InputKeyHitInjectsData() throws Exception {
        ToolDataChannel channel = new ToolDataChannel();
        ToolDataKey key = new ToolDataKey("defaultTenant", "edp-agent", "session-1", "session-1");
        Map<String, Object> data = Map.of("products", List.of(Map.of("name", "稳健理财A")));
        channel.store(key, "fund_recommend_result", data);
        VersatileInterruptRail rail = new VersatileInterruptRail(null, new EdpaSpringBootConfig.VersatileConfig(), channel);

        Map<String, Object> inputs = invokeBuildInputs(rail, Map.of(
                "query_description", "购买第一支理财产品",
                "query_intent", "理财购买",
                "input_key", "fund_recommend_result"));

        assertEquals(data, inputs.get("input_data"));
        assertEquals(data, inputs.get("business_data"));
        assertEquals("购买第一支理财产品", inputs.get("query"));
    }

    @Test
    void testBuildInputs_InputKeyMissInjectsEmptyMap() throws Exception {
        ToolDataChannel channel = new ToolDataChannel();
        VersatileInterruptRail rail = new VersatileInterruptRail(null, new EdpaSpringBootConfig.VersatileConfig(), channel);

        Map<String, Object> inputs = invokeBuildInputs(rail, Map.of(
                "query_description", "购买第一支理财产品",
                "query_intent", "理财购买",
                "input_key", "Fund_Recommend_Result"));

        assertTrue(((Map<?, ?>) inputs.get("input_data")).isEmpty());
        assertTrue(((Map<?, ?>) inputs.get("business_data")).isEmpty());
    }

    @Test
    void testBuildInputs_QueryDescriptionAutoFilled() throws Exception {
        ToolDataChannel channel = new ToolDataChannel();
        ToolDataKey key = new ToolDataKey("defaultTenant", "edp-agent", "session-1", "session-1");
        channel.store(key, "mcp_to_versatile_information", Map.of("query_description", "购买第一支理财产品"));
        VersatileInterruptRail rail = new VersatileInterruptRail(null, new EdpaSpringBootConfig.VersatileConfig(), channel);

        Map<String, Object> inputs = invokeBuildInputs(rail, Map.of("query_intent", "理财购买"));

        assertEquals("购买第一支理财产品", inputs.get("query"));
        assertEquals("购买第一支理财产品", inputs.get("query_description"));
    }

    @Test
    void testBeforeToolCall_ResumeInputBecomesToolResultWithoutAdapterCall() {
        VersatileInterruptRail rail = new VersatileInterruptRail(null, null, new ToolDataChannel());
        AgentCallbackContext ctx = buildContext();
        InteractiveInput resumeInput = new InteractiveInput();
        resumeInput.update("tool-call-1",
                "{\"source\":\"versatile\",\"status\":\"completed\",\"content\":\"{\\\"status\\\":\\\"success\\\"}\"}");
        ctx.getExtra().put(ToolInterruptionState.RESUME_USER_INPUT_KEY, resumeInput);

        rail.beforeToolCall(ctx);

        ToolCallInputs inputs = (ToolCallInputs) ctx.getInputs();
        assertEquals(Boolean.TRUE, ctx.getExtra().get("_skip_tool"));
        assertNotNull(inputs.getToolMsg());
        assertEquals("tool-call-1", inputs.getToolMsg().getToolCallId());
        assertTrue(String.valueOf(inputs.getToolResult()).contains("completed"));
        assertTrue(String.valueOf(inputs.getToolMsg().getContent()).contains("success"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeBuildInputs(VersatileInterruptRail rail, Map<String, Object> args) throws Exception {
        Method method = VersatileInterruptRail.class.getDeclaredMethod("buildInputs", Map.class, AgentCallbackContext.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(rail, args, buildContext());
    }

    private AgentCallbackContext buildContext() {
        ToolCall toolCall = new ToolCall();
        toolCall.setId("tool-call-1");
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName("call_versatile")
                .toolCall(toolCall)
                .build();
        return AgentCallbackContext.builder()
                .session(new AgentSessionApi("session-1"))
                .inputs(inputs)
                .build();
    }
}
