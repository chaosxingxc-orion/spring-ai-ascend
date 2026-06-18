/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan.a2a.tools;

import com.huawei.ascend.examples.travel.mainplan.a2a.constant.AgentConstants;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.util.List;
import java.util.Map;

/**
 * Tool that signals the agent needs more information from the user.
 *
 * <p>The actual interrupt/resume logic is handled by
 * {@link com.huawei.ascend.examples.travel.mainplan.a2a.rails.UserInputInterruptRail}.
 * This tool serves as the LLM-callable trigger — when the LLM determines
 * information is insufficient, it calls this tool, which the Rail intercepts.
 */
public class RequestUserInputTool extends LocalFunction {

    /**
     * Create the request user input tool.
     */
    public RequestUserInputTool() {
        super(
                ToolCard.builder()
                        .id(AgentConstants.TOOL_REQUEST_USER_INPUT)
                        .name(AgentConstants.TOOL_REQUEST_USER_INPUT)
                        .description("当用户提供的出差信息不充分时（如缺少目的地、出发日期等），向用户追问缺失信息")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "missing_fields", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string"),
                                                "description", "缺失的字段列表，如[\"目的地\",\"出发日期\"]"
                                        ),
                                        "follow_up_message", Map.of(
                                                "type", "string",
                                                "description", "向用户追问的自然语言消息"
                                        )
                                ),
                                "required", List.of("missing_fields", "follow_up_message")
                        ))
                        .build(),
                (inputs) -> "user_input_collected"
        );
    }
}
