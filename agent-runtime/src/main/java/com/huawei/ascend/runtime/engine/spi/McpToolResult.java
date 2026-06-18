package com.huawei.ascend.runtime.engine.spi;

import java.util.List;
import java.util.Map;

/** Runtime-neutral MCP tool result aligned with the MCP ToolResult shape. */
public record McpToolResult(
        List<Map<String, Object>> content,
        Object structuredContent,
        boolean isError,
        String errorCode,
        String message,
        Map<String, Object> meta,
        Map<String, Object> metadata) {
    public McpToolResult {
        content = content == null ? List.of() : content.stream()
                .map(item -> item == null ? Map.<String, Object>of() : Map.copyOf(item))
                .toList();
        errorCode = errorCode == null ? "" : errorCode;
        message = message == null ? "" : message;
        meta = meta == null ? Map.of() : Map.copyOf(meta);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static McpToolResult success(List<Map<String, Object>> content, Object structuredContent,
            Map<String, Object> meta, Map<String, Object> metadata) {
        return new McpToolResult(content, structuredContent, false, "", "", meta, metadata);
    }

    public static McpToolResult error(String errorCode, String message, Map<String, Object> metadata) {
        return new McpToolResult(
                List.of(Map.of("type", "text", "text", message == null ? "" : message)),
                null,
                true,
                errorCode,
                message,
                Map.of(),
                metadata);
    }
}
