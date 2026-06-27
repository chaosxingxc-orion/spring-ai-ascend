package com.huawei.ascend.examples.workmate.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class McpCallResultMapper {

    private McpCallResultMapper() {
    }

    public static Map<String, Object> toToolResult(McpSchema.CallToolResult result) {
        if (result == null) {
            return Map.of("success", false, "error", "empty MCP result");
        }
        if (Boolean.TRUE.equals(result.isError())) {
            return Map.of("success", false, "error", extractText(result.content()));
        }
        return Map.of("success", true, "data", Map.of("text", extractText(result.content())));
    }

    public static String extractText(List<McpSchema.Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (McpSchema.Content content : contents) {
            if (content instanceof McpSchema.TextContent textContent) {
                builder.append(textContent.text());
            } else {
                builder.append(String.valueOf(content));
            }
        }
        return builder.toString();
    }
}
