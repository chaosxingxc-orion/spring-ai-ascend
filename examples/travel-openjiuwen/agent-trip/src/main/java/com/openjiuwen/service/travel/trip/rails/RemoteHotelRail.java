/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.trip.rails;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.interrupt.BaseInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the canonical {@code A2AToolRail}: self-registers a {@code hotel} ToolCard and emits
 * {@code interrupt(InterruptRequest{message, context.agentName="agent-openjiuwen-travel-hotel"})}. The existing
 * {@code A2AEnabledServeOrchestrator} detects the resulting {@code __interaction__} chunk,
 * resolves {@code agentName="agent-openjiuwen-travel-hotel"} (registry key = the yaml {@code remote-agents[].name}),
 * and calls the remote hotel agent. This rail performs NO remote call and emits no chunk.
 */
public class RemoteHotelRail extends BaseInterruptRail {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final String TOOL_NAME = "hotel";
    private static final String AGENT_NAME = "agent-openjiuwen-travel-hotel";

    public RemoteHotelRail() {
        super(List.of(TOOL_NAME));
        ToolCard card = ToolCard.builder()
                .id(TOOL_NAME).name(TOOL_NAME)
                .description("委托远端酒店规划智能体查询酒店")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "remoteInput", Map.of(
                                        "type", "string",
                                        "description", "转发给酒店智能体的用户请求文本（含城市、入离日期、差标、偏好）")),
                        "required", List.of("remoteInput")))
                .build();
        getTools().add(card);
    }

    @Override
    protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall,
                                                 Object resumeInput) {
        if (resumeInput != null) {
            // Resume: skip the empty tool and return the remote result directly.
            // approve() would let tool execution continue → "Tool instance not found".
            return reject(resumeInput);
        }
        String userQuery = null;
        try {
            Map<String, Object> args = GSON.fromJson(toolCall.getArguments(), MAP_TYPE);
            Object msg = args.get("remoteInput");
            if (msg instanceof String s && !s.isBlank()) {
                userQuery = s;
            }
        } catch (Exception ignored) {
            // arguments parse failed; fall through to AGENT_NAME
        }
        var request = InterruptRequest.builder()
                .message(userQuery != null ? userQuery : AGENT_NAME)
                // _interrupt_kind="a2a_delegate" routes this through the orchestrator's remote
                // call path; without it the interrupt is misclassified as "ask_user" (INPUT_REQUIRED)
                // because resolveInterruptData only falls back to agentName at the *top* level,
                // which the handler never sets (agentName lives here, in context).
                .context(Map.of("agentName", AGENT_NAME, "_interrupt_kind", "a2a_delegate"))
                .build();
        return interrupt(request);
    }
}
