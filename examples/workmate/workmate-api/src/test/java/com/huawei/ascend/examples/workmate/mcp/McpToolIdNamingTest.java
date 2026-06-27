package com.huawei.ascend.examples.workmate.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpToolIdNamingTest {

    @Test
    void buildsStableOpenJiuwenToolId() {
        String id = McpToolIdNaming.openJiuwenToolId("docs-fs", "read_file");
        assertThat(id).isEqualTo("mcp__docs-fs__read_file");
        assertThat(McpToolIdNaming.parseOpenJiuwenToolId(id).toolName()).isEqualTo("read_file");
    }

    @Test
    void mapsJsonSchemaToInputParams() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object",
                java.util.Map.of("path", java.util.Map.of("type", "string")),
                List.of("path"),
                false,
                null,
                null);
        assertThat(McpToolIdNaming.toInputParams(schema)).containsEntry("type", "object");
    }
}
