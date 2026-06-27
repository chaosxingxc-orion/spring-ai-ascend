package com.huawei.ascend.examples.workmate.mcp;

import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HttpMcpServerClient implements McpServerClient {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMcpServerClient.class);

    private final String serverId;
    private final McpSyncClient client;

    HttpMcpServerClient(String serverId, McpServerConfig server, Duration timeout) {
        this.serverId = serverId;
        McpHttpTarget target = McpHttpTarget.parse(server.url(), server.endpoint());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        for (Map.Entry<String, String> entry : server.headers().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }
        var transport = buildTransport(server.transport(), target, requestBuilder);
        this.client = McpClient.sync(transport).requestTimeout(timeout).build();
        LOG.info(
                "Initializing HTTP MCP client id={} transport={} base={} endpoint={}",
                serverId,
                server.transport(),
                target.baseUri(),
                target.endpoint());
        client.initialize();
    }

    private static McpClientTransport buildTransport(
            String transportType, McpHttpTarget target, HttpRequest.Builder requestBuilder) {
        String type = transportType == null ? "streamable-http" : transportType;
        return switch (type) {
            case "sse" -> HttpClientSseClientTransport.builder(target.baseUri())
                    .sseEndpoint(target.endpoint())
                    .requestBuilder(requestBuilder)
                    .build();
            case "streamable-http" -> HttpClientStreamableHttpTransport.builder(target.baseUri())
                    .endpoint(target.endpoint())
                    .requestBuilder(requestBuilder)
                    .build();
            default -> throw new IllegalArgumentException("Unsupported MCP HTTP transport: " + type);
        };
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
            LOG.warn("Failed to close HTTP MCP client id={}", serverId, ex);
        }
    }
}
