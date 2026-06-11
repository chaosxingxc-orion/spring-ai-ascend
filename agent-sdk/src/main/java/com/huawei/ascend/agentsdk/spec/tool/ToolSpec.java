package com.huawei.ascend.agentsdk.spec.tool;

import java.util.Map;

public record ToolSpec(
        String name,
        String description,
        Map<String, Object> inputSchema,
        ToolRef ref) {

    public ToolSpec {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}

