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

public final class TravelTodoSearchTool extends LocalFunction {

    public TravelTodoSearchTool() {
        super(buildCard(), TravelTodoSearchTool::execute);
    }

    static Map<String, Object> execute(Map<String, Object> inputs) {
        Map<String, Object> out = MockTravelMcpData.todos();
        out.put("userId", asString(inputs.get("userId")));
        out.put("keyword", asString(inputs.get("keyword")));
        out.put("startDate", asString(inputs.get("startDate")));
        out.put("endDate", asString(inputs.get("endDate")));
        return out;
    }

    private static ToolCard buildCard() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("userId", Map.of("type", "string", "description", "用户 ID"));
        props.put("keyword", Map.of("type", "string", "description", "关键词，可空"));
        props.put("startDate", Map.of("type", "string", "description", "开始日期，可空"));
        props.put("endDate", Map.of("type", "string", "description", "结束日期，可空"));

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", props);
        inputParams.put("required", List.of("userId"));

        return ToolCard.builder()
                .id(TaskCollectorAgentConstants.TOOL_TODO_SEARCH)
                .name(TaskCollectorAgentConstants.TOOL_TODO_SEARCH)
                .description("查询与本次出差相关的待办事项")
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
