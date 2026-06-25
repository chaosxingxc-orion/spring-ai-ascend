/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector.mcp;

import com.huawei.ascend.examples.travel.taskcollector.TaskCollectorAgentConstants;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TravelPolicyLookupTool extends LocalFunction {

    public TravelPolicyLookupTool() {
        super(buildCard(), TravelPolicyLookupTool::execute);
    }

    static Map<String, Object> execute(Map<String, Object> inputs) {
        Map<String, Object> out = MockTravelMcpData.policy(asString(inputs.get("city")));
        out.put("userId", asString(inputs.get("userId")));
        out.put("travelType", asString(inputs.getOrDefault("travelType", "business")));
        return out;
    }

    private static ToolCard buildCard() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("userId", Map.of("type", "string", "description", "用户 ID"));
        props.put("city", Map.of("type", "string", "description", "目的城市，可空"));
        props.put("travelType", Map.of("type", "string", "description", "出差类型，默认 business"));

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", props);
        inputParams.put("required", List.of("userId"));

        return ToolCard.builder()
                .id(TaskCollectorAgentConstants.TOOL_POLICY_LOOKUP)
                .name(TaskCollectorAgentConstants.TOOL_POLICY_LOOKUP)
                .description("查询差旅规则，包括酒店预算、酒店星级、协议品牌、交通标准")
                .inputParams(inputParams)
                .build();
    }

    public static Map<String, Object> invokeDirectly(Map<String, Object> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        return execute(inputs);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
