package com.huawei.ascend.examples.workmate.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpCallResultMapperTest {

    @Test
    void mapsTextContent() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("hello readme")),
                false,
                null,
                null);
        Map<String, Object> mapped = McpCallResultMapper.toToolResult(result);
        assertThat(mapped.get("success")).isEqualTo(true);
    }
}
