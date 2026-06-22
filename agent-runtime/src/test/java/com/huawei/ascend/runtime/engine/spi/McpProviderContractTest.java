package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpProviderContractTest {

    @Test
    void toolSpecNormalizesNullCollections() {
        McpToolSpec spec = new McpToolSpec(" server ", " tool ", null, null, null, null, null, null, null);

        assertThat(spec.serverId()).isEqualTo("server");
        assertThat(spec.name()).isEqualTo("tool");
        assertThat(spec.title()).isEmpty();
        assertThat(spec.description()).isEmpty();
        assertThat(spec.inputSchema()).isEmpty();
        assertThat(spec.outputSchema()).isEmpty();
        assertThat(spec.annotations()).isEmpty();
        assertThat(spec.meta()).isEmpty();
        assertThat(spec.metadata()).isEmpty();
    }

    @Test
    void toolResultPreservesMcpShapeAndRuntimeErrorFields() {
        McpToolResult result = new McpToolResult(
                List.of(Map.of("type", "text", "text", "pong")),
                Map.of("value", "pong"),
                true,
                "MCP_TOOL_NOT_FOUND",
                "tool not found",
                Map.of("mcp", "meta"),
                Map.of("serverId", "time"));

        assertThat(result.content()).singleElement()
                .satisfies(item -> assertThat(item).containsEntry("text", "pong"));
        assertThat(result.structuredContent()).isEqualTo(Map.of("value", "pong"));
        assertThat(result.isError()).isTrue();
        assertThat(result.errorCode()).isEqualTo("MCP_TOOL_NOT_FOUND");
        assertThat(result.meta()).containsEntry("mcp", "meta");
        assertThat(result.metadata()).containsEntry("serverId", "time");
    }
}
