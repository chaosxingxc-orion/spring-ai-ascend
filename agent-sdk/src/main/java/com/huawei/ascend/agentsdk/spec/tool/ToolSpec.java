package com.huawei.ascend.agentsdk.spec.tool;

import java.util.Map;

public record ToolSpec(
        String name,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        ToolRef ref,
        boolean localCache) {

    public ToolSpec {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
    }
}

