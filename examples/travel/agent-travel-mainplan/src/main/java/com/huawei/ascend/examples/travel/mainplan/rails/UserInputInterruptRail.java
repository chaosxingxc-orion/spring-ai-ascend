/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan.rails;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.travel.mainplan.constant.AgentConstants;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.interrupt.BaseInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;

import java.util.List;
import java.util.Map;

/**
 * Rail that intercepts the {@code request_user_input} tool call.
 * <p>
 * When the LLM calls {@code request_user_input} to ask for missing information,
 * this rail triggers an interrupt so the user can provide input.
 * On resume (user has provided input), the rail approves and passes the
 * user's response as modified tool arguments.
 */
public class UserInputInterruptRail extends BaseInterruptRail {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Create the rail, intercepting the {@code request_user_input} tool.
     */
    public UserInputInterruptRail() {
        super(List.of(AgentConstants.TOOL_REQUEST_USER_INPUT));
    }

    @Override
    protected InterruptDecision resolveInterrupt(
            AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
        if (userInput == null) {
            String followUpMessage = extractFollowUpMessage(toolCall.getArguments());
            return interrupt(InterruptRequest.builder()
                    .interruptId(toolCall.getId())
                    .message(followUpMessage)
                    .context(Map.of("tool_call_id", toolCall.getId()))
                    .build());
        }
        return approve(toJsonArgs(String.valueOf(userInput)));
    }

    private String extractFollowUpMessage(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return "请提供更多出差信息。";
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(argsJson,
                    new TypeReference<Map<String, Object>>() {});
            Object message = parsed.get("follow_up_message");
            if (message instanceof String s && !s.isBlank()) {
                return s;
            }
            return "请提供更多出差信息。";
        } catch (Exception e) {
            return "请提供更多出差信息。";
        }
    }

    private String toJsonArgs(String userInput) {
        String escaped = userInput
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "{\"response\":\"" + escaped + "\"}";
    }
}