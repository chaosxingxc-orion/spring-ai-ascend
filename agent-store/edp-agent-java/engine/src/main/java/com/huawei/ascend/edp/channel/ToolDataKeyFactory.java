package com.huawei.ascend.edp.channel;

import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

/**
 * ToolDataChannel 四元组隔离键工厂。
 */
public final class ToolDataKeyFactory {

    private static final String DEFAULT_TENANT_ID = "defaultTenant";
    private static final String DEFAULT_AGENT_ID = "edp-agent";
    private static final String DEFAULT_CONTEXT_ID = "defaultContext";
    private static final String DEFAULT_TASK_ID = "defaultTask";

    private ToolDataKeyFactory() {
    }

    public static ToolDataKey fromContext(AgentCallbackContext ctx, EdpConfig edpConfig) {
        String tenantId = resolveFromExtra(ctx, "tenantId", "tenant_id");
        String agentId = resolveFromExtra(ctx, "agentId", "agent_id");
        String contextId = resolveFromExtra(ctx, "contextId", "context_id");
        String taskId = resolveFromExtra(ctx, "taskId", "task_id");

        if (isBlank(contextId) && ctx != null && ctx.getSession() != null) {
            contextId = ctx.getSession().getSessionId();
        }
        if (isBlank(taskId)) {
            taskId = contextId;
        }

        return new ToolDataKey(
                nonBlank(tenantId, DEFAULT_TENANT_ID),
                nonBlank(agentId, DEFAULT_AGENT_ID),
                nonBlank(contextId, DEFAULT_CONTEXT_ID),
                nonBlank(taskId, DEFAULT_TASK_ID));
    }

    private static String resolveFromExtra(AgentCallbackContext ctx, String camelKey, String snakeKey) {
        if (ctx == null || ctx.getExtra() == null) {
            return "";
        }
        Object value = ctx.getExtra().get(camelKey);
        if (value == null) {
            value = ctx.getExtra().get(snakeKey);
        }
        return value != null ? String.valueOf(value) : "";
    }

    private static String resolveToolCallId(AgentCallbackContext ctx) {
        if (ctx == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return "";
        }
        ToolCall toolCall = inputs.getToolCall();
        return toolCall != null ? toolCall.getId() : "";
    }

    private static String nonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
