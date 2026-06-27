package com.huawei.ascend.examples.workmate.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpGatewayTest {

    private static WorkmateMcpProperties props() {
        return new WorkmateMcpProperties(true, 30, 50, 3, 500, List.of());
    }

    @Test
    void listsAndCallsRegisteredServerTools() {
        McpGateway gateway = new McpGateway(props());
        gateway.register(new FakeMcpClient("docs-fs", List.of(
                McpSchema.Tool.builder().name("read_file").description("read").build())));

        assertThat(gateway.listServers()).hasSize(1);
        assertThat(gateway.listTools()).extracting(McpGateway.McpToolDescriptor::toolName)
                .containsExactly("read_file");

        McpSchema.CallToolResult result = gateway.callTool("docs-fs", "read_file", Map.of("path", "README.md"));
        assertThat(McpCallResultMapper.extractText(result.content())).contains("README");
    }

    @Test
    void skipsInvalidSchemaToolsAndReportsCount() {
        McpGateway gateway = new McpGateway(props());
        gateway.register(new FakeMcpClient(
                "docs-fs",
                List.of(
                        McpSchema.Tool.builder().name("good").description("ok").build(),
                        McpSchema.Tool.builder()
                                .name("bad")
                                .description("bad")
                                .inputSchema(emptySchema())
                                .build())));

        assertThat(gateway.listTools()).extracting(McpGateway.McpToolDescriptor::toolName)
                .containsExactly("good");
        McpGateway.McpServerSummary summary = gateway.serverSummary("docs-fs");
        assertThat(summary.invalidSchemaCount()).isEqualTo(1);
        assertThat(summary.toolCount()).isEqualTo(1);
    }

    @Test
    void recordsConnectionErrorOnSummary() {
        McpGateway gateway = new McpGateway(props());
        gateway.recordConnectionError("docs-fs", "connection refused");
        McpGateway.McpServerSummary summary = gateway.serverSummary("docs-fs");
        assertThat(summary.connected()).isFalse();
        assertThat(summary.lastError()).isEqualTo("connection refused");
    }

    @Test
    void cachesServerSummaryWithoutRepeatedToolListing() {
        McpGateway gateway = new McpGateway(props());
        CountingMcpClient client = new CountingMcpClient(
                "docs-fs",
                List.of(McpSchema.Tool.builder().name("read_file").description("read").build()));
        gateway.register(client);

        gateway.serverSummary("docs-fs");
        gateway.serverSummary("docs-fs");

        assertThat(client.listToolsCalls()).isEqualTo(1);
    }

    private static McpSchema.JsonSchema emptySchema() {
        return new McpSchema.JsonSchema(null, Map.of(), null, null, null, null);
    }

    private static class FakeMcpClient implements McpServerClient {
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
                    List.of(new McpSchema.TextContent("README content for " + arguments.get("path"))),
                    false,
                    null,
                    null);
        }

        @Override
        public void close() {
        }
    }

    private static final class CountingMcpClient implements McpServerClient {
        private final String serverId;
        private final List<McpSchema.Tool> tools;
        private int listToolsCalls;

        CountingMcpClient(String serverId, List<McpSchema.Tool> tools) {
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
            listToolsCalls += 1;
            return tools;
        }

        int listToolsCalls() {
            return listToolsCalls;
        }

        @Override
        public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
            return new McpSchema.CallToolResult(List.of(), false, null, null);
        }

        @Override
        public void close() {
        }
    }
}
