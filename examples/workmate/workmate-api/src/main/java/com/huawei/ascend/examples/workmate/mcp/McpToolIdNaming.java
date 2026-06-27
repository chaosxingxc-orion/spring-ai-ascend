package com.huawei.ascend.examples.workmate.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.Map;

public final class McpToolIdNaming {

    private McpToolIdNaming() {
    }

    public static String openJiuwenToolId(String serverId, String mcpToolName) {
        return "mcp__" + sanitize(serverId) + "__" + sanitize(mcpToolName);
    }

    public static McpToolRef parseOpenJiuwenToolId(String openJiuwenToolId) {
        if (!openJiuwenToolId.startsWith("mcp__")) {
            throw new IllegalArgumentException("Not an MCP proxy tool: " + openJiuwenToolId);
        }
        String[] parts = openJiuwenToolId.split("__", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid MCP proxy tool id: " + openJiuwenToolId);
        }
        return new McpToolRef(parts[1], parts[2]);
    }

    public record McpToolRef(String serverId, String toolName) {
    }

    static Map<String, Object> toInputParams(McpSchema.JsonSchema schema) {
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", schema != null && schema.type() != null ? schema.type() : "object");
        inputParams.put("properties", schema != null && schema.properties() != null ? schema.properties() : Map.of());
        if (schema != null && schema.required() != null && !schema.required().isEmpty()) {
            inputParams.put("required", schema.required());
        }
        return inputParams;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
