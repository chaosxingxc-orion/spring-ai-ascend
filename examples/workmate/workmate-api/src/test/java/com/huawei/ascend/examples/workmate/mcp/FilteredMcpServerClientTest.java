package com.huawei.ascend.examples.workmate.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FilteredMcpServerClientTest {

    @Test
    void filtersListedToolsAndBlocksDisallowedCalls() {
        FakeMcpClient delegate = new FakeMcpClient(
                "qieman",
                List.of(
                        tool("GetCurrentTime"),
                        tool("SearchFunds"),
                        tool("GetBondIndicator")));
        FilteredMcpServerClient filtered =
                new FilteredMcpServerClient(delegate, List.of("GetCurrentTime", "SearchFunds"));

        assertThat(filtered.listTools()).extracting(McpSchema.Tool::name)
                .containsExactly("GetCurrentTime", "SearchFunds");

        filtered.callTool("SearchFunds", Map.of("keyword", "test"));
        assertThatThrownBy(() -> filtered.callTool("GetBondIndicator", Map.of()))
                .isInstanceOf(McpToolNotAllowedException.class);
    }

    @Test
    void emptyAllowlistPassesThrough() {
        FakeMcpClient delegate = new FakeMcpClient("qieman", List.of(tool("A"), tool("B")));
        FilteredMcpServerClient filtered = new FilteredMcpServerClient(delegate, List.of());

        assertThat(filtered.listTools()).hasSize(2);
        filtered.callTool("A", Map.of());
    }

    private static McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder().name(name).description(name).build();
    }

    private static final class FakeMcpClient implements McpServerClient {
        private final String serverId;
        private final List<McpSchema.Tool> tools;

        FakeMcpClient(String serverId, List<McpSchema.Tool> tools) {
            this.serverId = serverId;
            this.tools = tools;
        }

        @Override
        public String serverId() {
            return serverId;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public List<McpSchema.Tool> listTools() {
            return tools;
        }

        @Override
        public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("ok:" + toolName)), false, null, null);
        }

        @Override
        public void close() {
        }
    }
}
