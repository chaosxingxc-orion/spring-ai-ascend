package com.huawei.ascend.examples.runtime.middleware.mcp.remotejson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.mcp.HttpMcpProvider;
import com.huawei.ascend.runtime.engine.mcp.McpProperties;
import com.huawei.ascend.runtime.engine.spi.McpToolResult;
import com.huawei.ascend.runtime.engine.spi.McpToolSpec;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpRemoteJsonRuntimeApplicationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    private Path tempDir;

    @Test
    void jsonConfiguredMcpProviderDiscoversAndCallsRemoteServer() throws Exception {
        HttpServer server = startMcpServer();
        try {
            Path configFile = tempDir.resolve("mcp-servers.json");
            Files.writeString(configFile, JSON.writeValueAsString(Map.of(
                    "servers", List.of(Map.of(
                            "serverId", "remote-tools",
                            "transport", "streamable-http",
                            "url", "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp")))));

            HttpMcpProvider provider = new HttpMcpProvider(
                    RemoteMcpServerConfigLoader.load(configFile, JSON), JSON);

            List<McpToolSpec> tools = provider.listTools(null);
            McpToolResult result = provider.callTool(null, "remote-tools", "get_current_date", Map.of());

            assertThat(tools).extracting(McpToolSpec::name).contains("get_current_date");
            assertThat(result.isError()).isFalse();
            assertThat(String.valueOf(result.structuredContent())).contains("date");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mcpServersConfigUsesObjectKeyAsServerIdAndTypeAsTransport() throws Exception {
        Path configFile = tempDir.resolve("modelscope-mcp-servers.json");
        Files.writeString(configFile, JSON.writeValueAsString(Map.of(
                "mcpServers", Map.of(
                        "howtocook-mcp", Map.of(
                                "type", "sse",
                                "url", "https://mcp.api-inference.modelscope.net/136ad5a3226b4d/sse")))));

        McpProperties properties = RemoteMcpServerConfigLoader.load(configFile, JSON);

        assertThat(properties.getServers())
                .singleElement()
                .satisfies(server -> {
                    assertThat(server.getServerId()).isEqualTo("howtocook-mcp");
                    assertThat(server.getTransport()).isEqualTo("sse");
                    assertThat(server.getUrl()).isEqualTo("https://mcp.api-inference.modelscope.net/136ad5a3226b4d/sse");
                });
    }

    private static HttpServer startMcpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            Map<?, ?> request = JSON.readValue(exchange.getRequestBody(), Map.class);
            Object id = request.get("id");
            String method = String.valueOf(request.get("method"));
            if ("notifications/initialized".equals(method)) {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
                return;
            }
            Map<String, Object> response = switch (method) {
                case "initialize" -> response(id, Map.of(
                        "protocolVersion", "2025-06-18",
                        "capabilities", Map.of("tools", Map.of("listChanged", false))));
                case "tools/list" -> response(id, Map.of("tools", List.of(Map.of(
                        "name", "get_current_date",
                        "description", "Return current date",
                        "inputSchema", Map.of("type", "object", "properties", Map.of())))));
                case "tools/call" -> response(id, Map.of(
                        "content", List.of(Map.of("type", "text", "text", "2026-06-16")),
                        "structuredContent", Map.of("date", "2026-06-16"),
                        "isError", false));
                default -> Map.of("jsonrpc", "2.0", "id", id,
                        "error", Map.of("code", -32601, "message", "method not found"));
            };
            byte[] body = JSON.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static Map<String, Object> response(Object id, Map<String, Object> result) {
        return Map.of("jsonrpc", "2.0", "id", id, "result", result);
    }
}
