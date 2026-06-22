package com.huawei.ascend.runtime.engine.spi;

import java.util.Map;

/** Runtime-neutral MCP tool descriptor aligned with the MCP Tool shape. */
public record McpToolSpec(
        String serverId,
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations,
        Map<String, Object> meta,
        Map<String, Object> metadata) {
    public McpToolSpec {
        serverId = normalize(serverId);
        name = normalize(name);
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        inputSchema = copyOrEmpty(inputSchema);
        outputSchema = copyOrEmpty(outputSchema);
        annotations = copyOrEmpty(annotations);
        meta = copyOrEmpty(meta);
        metadata = copyOrEmpty(metadata);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Object> copyOrEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }
}
