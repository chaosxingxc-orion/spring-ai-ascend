/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan.tools;

import com.huawei.ascend.examples.travel.mainplan.constant.AgentConstants;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Tool that dispatches a natural language instruction to the TravelPlanAgent.
 * <p>
 * Receives a {@link Function}{@code <String, String>} as the dispatch mechanism.
 * The tool does not know how the TravelPlanAgent is implemented — it simply
 * passes a natural language instruction string and returns the result string.
 */
public class DispatchTravelPlanTool extends LocalFunction {

    /**
     * Create a dispatch tool with the given dispatcher function.
     *
     * @param dispatcher function that receives a natural language instruction
     *                   and returns the TravelPlanAgent's response
     */
    public DispatchTravelPlanTool(Function<String, String> dispatcher) {
        super(
                ToolCard.builder()
                        .id(AgentConstants.TOOL_DISPATCH_TRAVEL_PLAN)
                        .name(AgentConstants.TOOL_DISPATCH_TRAVEL_PLAN)
                        .description("将自然语言任务指令发送给行程规划智能体，获取完整行程方案（含交通、酒店等）")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "instruction", Map.of(
                                                "type", "string",
                                                "description",
                                                "自然语言出差需求描述，如'张三从上海到北京出差，"
                                                        + "去程日期是2026年8月2日，返程日期是2026年8月3日。'"
                                        )
                                ),
                                "required", List.of("instruction")
                        ))
                        .build(),
                (inputs) -> {
                    String instruction = (String) inputs.get("instruction");
                    try {
                        return dispatcher.apply(instruction);
                    } catch (Exception e) {
                        return "行程规划智能体调用失败：" + e.getMessage();
                    }
                }
        );
    }
}