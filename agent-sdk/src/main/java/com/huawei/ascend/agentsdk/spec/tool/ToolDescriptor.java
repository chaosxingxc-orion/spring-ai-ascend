package com.huawei.ascend.agentsdk.spec.tool;

import java.util.Map;

public record ToolDescriptor(
        String name,
        String description,
        Map<String, Object> inputSchema) {

    public ToolDescriptor {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}

