package com.huawei.ascend.edp.tools;

import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.foundation.tool.Tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EDPAgent 轻量 Todo 写入工具。
 */
public final class LiteTodoWriteTool {

    private LiteTodoWriteTool() {
    }

    public static Tool build(EdpConfig edpConfig) {
        return EdpaBusinessTools.localTool(EdpaBusinessTools.TOOL_LITE_TODO_WRITE,
                "覆盖式更新 EDPAgent 轻量 Todo 列表，步骤 Schema 来自 edp-config.yaml。",
                liteTodoSchema(edpConfig),
                inputs -> Map.of(
                        "tool", EdpaBusinessTools.TOOL_LITE_TODO_WRITE,
                        "status", "accepted",
                        "todos", inputs.getOrDefault("todos", List.of())));
    }

    private static Map<String, Object> liteTodoSchema(EdpConfig edpConfig) {
        List<Integer> configuredStepIds = edpConfig == null || edpConfig.getTodolistSteps() == null
                ? List.of()
                : edpConfig.getTodolistSteps().stream()
                        .map(EdpConfig.TodolistStep::getStepId)
                        .toList();
        Map<String, Object> itemProperties = new HashMap<>();
        itemProperties.put("step_id", Map.of(
                "type", "integer",
                "description", "edp-config.yaml 中定义的步骤 ID",
                "enum", configuredStepIds));
        itemProperties.put("status", Map.of(
                "type", "string",
                "description", "步骤状态",
                "enum", List.of("pending", "done")));
        itemProperties.put("note", EdpaBusinessTools.stringProp("步骤说明"));
        return EdpaBusinessTools.objectSchema(Map.of(
                "todos", Map.of(
                        "type", "array",
                        "description", "覆盖式 Todo 列表",
                        "items", Map.of(
                                "type", "object",
                                "properties", itemProperties,
                                "required", List.of("step_id", "status")))),
                List.of("todos"));
    }
}
