package com.huawei.ascend.edp.channel;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ToolDataKeyFactory 单元测试。
 */
class ToolDataKeyFactoryTest {

    @Test
    void testFromContext_UsesSessionAsDefaultTaskId() {
        ToolCall toolCall = new ToolCall();
        toolCall.setId("tool-call-1");
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName("call_mcp")
                .toolCall(toolCall)
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .session(new AgentSessionApi("session-1"))
                .inputs(inputs)
                .build();

        ToolDataKey key = ToolDataKeyFactory.fromContext(ctx, null);

        assertEquals("defaultTenant", key.getTenantId());
        assertEquals("edp-agent", key.getAgentId());
        assertEquals("session-1", key.getContextId());
        assertEquals("session-1", key.getTaskId());
    }

    @Test
    void testFromContext_UsesExtraOverrides() {
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .session(new AgentSessionApi("session-1"))
                .extra(Map.of(
                        "tenantId", "tenant-a",
                        "agentId", "agent-a",
                        "contextId", "context-a",
                        "taskId", "task-a"))
                .build();

        ToolDataKey key = ToolDataKeyFactory.fromContext(ctx, null);

        assertEquals("tenant-a", key.getTenantId());
        assertEquals("agent-a", key.getAgentId());
        assertEquals("context-a", key.getContextId());
        assertEquals("task-a", key.getTaskId());
    }

    @Test
    void testFromContext_NoNullFields() {
        ToolDataKey key = ToolDataKeyFactory.fromContext(null, null);

        assertNotNull(key.getTenantId());
        assertNotNull(key.getAgentId());
        assertNotNull(key.getContextId());
        assertNotNull(key.getTaskId());
    }
}
