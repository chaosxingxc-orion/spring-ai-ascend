package com.huawei.ascend.examples.workmate.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StdioMcpServerClient implements McpServerClient {

    private static final Logger LOG = LoggerFactory.getLogger(StdioMcpServerClient.class);

    private final String serverId;
    private final McpSyncClient client;

    StdioMcpServerClient(
            String serverId, String command, List<String> args, Map<String, String> env, Duration timeout) {
        this.serverId = serverId;
        ServerParameters.Builder builder = ServerParameters.builder(command);
        if (!args.isEmpty()) {
            builder.args(args.toArray(String[]::new));
        }
        if (env != null && !env.isEmpty()) {
            builder.env(env);
        }
        StdioClientTransport transport = new StdioClientTransport(builder.build(), McpJsonDefaults.getMapper());
        this.client = McpClient.sync(transport).requestTimeout(timeout).build();
        LOG.info("Initializing MCP server client id={} command={}", serverId, command);
        client.initialize();
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public boolean isConnected() {
        return client.isInitialized();
    }

    @Override
    public List<McpSchema.Tool> listTools() {
        return client.listTools().tools();
    }

    @Override
    public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
                .name(toolName)
                .arguments(arguments)
                .build();
        return client.callTool(request);
    }

    @Override
    public void close() {
        try {
            client.closeGracefully();
        } catch (RuntimeException ex) {
            LOG.warn("Failed to close MCP client id={}", serverId, ex);
        }
    }
}
