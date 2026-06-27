package com.huawei.ascend.examples.workmate.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Filters MCP tools with unusable input schemas (G22 invalidSchema). */
final class McpToolSchemaValidator {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolSchemaValidator.class);

    private McpToolSchemaValidator() {
    }

    static List<McpSchema.Tool> filterValid(String serverId, List<McpSchema.Tool> tools) {
        List<McpSchema.Tool> valid = new ArrayList<>();
        for (McpSchema.Tool tool : tools) {
            if (isValid(tool)) {
                valid.add(tool);
            } else {
                LOG.warn(
                        "Skipping MCP tool with invalid schema server={} tool={}",
                        serverId,
                        tool != null ? tool.name() : "<null>");
            }
        }
        return valid;
    }

    static int countInvalid(String serverId, List<McpSchema.Tool> tools) {
        int invalid = 0;
        for (McpSchema.Tool tool : tools) {
            if (!isValid(tool)) {
                invalid++;
            }
        }
        return invalid;
    }

    static boolean isValid(McpSchema.Tool tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            return false;
        }
        McpSchema.JsonSchema schema = tool.inputSchema();
        if (schema == null) {
            return true;
        }
        boolean hasType = schema.type() != null && !schema.type().isBlank();
        boolean hasProperties = schema.properties() != null && !schema.properties().isEmpty();
        return hasType || hasProperties;
    }
}
