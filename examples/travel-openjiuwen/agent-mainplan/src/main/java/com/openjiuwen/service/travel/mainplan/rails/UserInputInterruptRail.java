/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.service.travel.mainplan.rails;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.interrupt.BaseInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Rail that intercepts the {@code request_user_input} tool call.
 *
 * <p>When the LLM calls {@code request_user_input} to ask for missing information,
 * this rail triggers an interrupt so the user can provide input.
 * On resume (user has provided input), the rail approves and passes the
 * user's response as modified tool arguments.
 *
 * <p>Unlike the remote-delegation rails, this interrupt carries no
 * {@code context.agentName} — it is a USER-facing interrupt, not a remote-delegation one.
 */
public class UserInputInterruptRail extends BaseInterruptRail {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    /** Tool identifier intercepted by this rail (mirrors RequestUserInputTool.TOOL_REQUEST_USER_INPUT). */
    private static final String TOOL_REQUEST_USER_INPUT = "request_user_input";

    /**
     * Create the rail, intercepting the {@code request_user_input} tool.
     */
    public UserInputInterruptRail() {
        super(List.of(TOOL_REQUEST_USER_INPUT));
    }

    @Override
    protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
        if (userInput != null) {
            return approve(toJsonArgs(String.valueOf(userInput)));
        }
        String argsJson = toolCall == null ? null : toolCall.getArguments();
        return interrupt(InterruptRequest.builder()
                .message(extractFollowUpMessage(argsJson))
                .build());
    }

    private String extractFollowUpMessage(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return "请提供更多出差信息。";
        }
        try {
            Map<String, Object> parsed = GSON.fromJson(argsJson, MAP_TYPE);
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
