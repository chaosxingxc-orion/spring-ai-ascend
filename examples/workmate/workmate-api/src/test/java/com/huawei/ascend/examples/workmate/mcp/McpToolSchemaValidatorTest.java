package com.huawei.ascend.examples.workmate.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolSchemaValidatorTest {

    @Test
    void acceptsToolWithoutSchemaOrWithObjectType() {
        assertThat(McpToolSchemaValidator.isValid(
                        McpSchema.Tool.builder().name("a").description("d").build()))
                .isTrue();
        assertThat(McpToolSchemaValidator.isValid(McpSchema.Tool.builder()
                        .name("b")
                        .description("d")
                        .inputSchema(objectSchema())
                        .build()))
                .isTrue();
    }

    @Test
    void rejectsNullToolOrEmptySchemaShell() {
        assertThat(McpToolSchemaValidator.isValid(null)).isFalse();
        assertThat(McpToolSchemaValidator.isValid(McpSchema.Tool.builder()
                        .name("bad")
                        .description("d")
                        .inputSchema(emptySchema())
                        .build()))
                .isFalse();
    }

    @Test
    void filterValidRemovesInvalidTools() {
        List<McpSchema.Tool> filtered = McpToolSchemaValidator.filterValid(
                "srv",
                List.of(
                        McpSchema.Tool.builder().name("ok").build(),
                        McpSchema.Tool.builder()
                                .name("no")
                                .inputSchema(emptySchema())
                                .build()));
        assertThat(filtered).extracting(McpSchema.Tool::name).containsExactly("ok");
        assertThat(McpToolSchemaValidator.countInvalid("srv", filtered)).isZero();
    }

    private static McpSchema.JsonSchema emptySchema() {
        return new McpSchema.JsonSchema(null, Map.of(), null, null, null, null);
    }

    private static McpSchema.JsonSchema objectSchema() {
        return new McpSchema.JsonSchema("object", Map.of(), null, null, null, null);
    }
}
