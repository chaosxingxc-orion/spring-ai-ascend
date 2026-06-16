package com.huawei.ascend.runtime.engine.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.McpToolResult;
import com.huawei.ascend.runtime.engine.spi.McpToolSpec;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpMcpProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listsAndCallsToolsThroughJsonRpcHttp() throws Exception {
        startServer(200, this::mcpResponse);
        HttpMcpProvider provider = new HttpMcpProvider(properties(serverUrl(), Map.of()), objectMapper);

        List<McpToolSpec> tools = provider.listTools(context());
        McpToolResult result = provider.callTool(context(), "local-time", "get_current_time", Map.of());

        assertThat(tools)
                .singleElement()
                .satisfies(tool -> {
                    assertThat(tool.serverId()).isEqualTo("local-time");
                    assertThat(tool.name()).isEqualTo("get_current_time");
                    assertThat(tool.inputSchema()).containsEntry("type", "object");
                });
        assertThat(result.isError()).isFalse();
        assertThat(result.content())
                .singleElement()
                .satisfies(item -> assertThat(item).containsEntry("text", "12:00:00"));
        assertThat(result.structuredContent()).isEqualTo(Map.of("time", "12:00:00"));
    }

    @Test
    void listsAndCallsToolsThroughSseTransport() throws Exception {
        startSseServer();
        McpProperties properties = properties(serverUrl("/sse"), Map.of());
        properties.getServers().getFirst().setTransport("sse");
        HttpMcpProvider provider = new HttpMcpProvider(properties, objectMapper);

        List<McpToolSpec> tools = provider.listTools(context());
        McpToolResult result = provider.callTool(context(), "local-time", "get_current_time", Map.of());

        assertThat(tools).extracting(McpToolSpec::name).containsExactly("get_current_time");
        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent()).isEqualTo(Map.of("time", "12:00:00"));
    }

    @Test
    void mapsAuthenticationFailureToToolResultError() throws Exception {
        startServer(401, request -> "{}");
        HttpMcpProvider provider = new HttpMcpProvider(properties(serverUrl(), Map.of("Authorization", "Bearer bad")),
                objectMapper);

        McpToolResult result = provider.callTool(context(), "local-time", "get_current_time", Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.errorCode()).isEqualTo("MCP_AUTH_FAILED");
    }

    @Test
    void mapsJsonRpcToolNotFoundToToolResultError() throws Exception {
        startServer(200, request -> """
                {"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"tool not found"}}
                """);
        HttpMcpProvider provider = new HttpMcpProvider(properties(serverUrl(), Map.of()), objectMapper);

        McpToolResult result = provider.callTool(context(), "local-time", "missing_tool", Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.errorCode()).isEqualTo("MCP_TOOL_NOT_FOUND");
    }

    private String mcpResponse(String request) throws IOException {
        Map<?, ?> body = objectMapper.readValue(request, Map.class);
        String method = String.valueOf(body.get("method"));
        if ("tools/list".equals(method)) {
            return """
                    {"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"get_current_time","title":"Current time","description":"Return current time","inputSchema":{"type":"object","properties":{}}}]}}
                    """;
        }
        if ("tools/call".equals(method)) {
            return """
                    {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"12:00:00"}],"structuredContent":{"time":"12:00:00"},"isError":false}}
                    """;
        }
        return """
                {"jsonrpc":"2.0","id":0,"result":{}}
                """;
    }

    private interface ResponseFactory {
        String respond(String request) throws IOException;
    }

    private void startServer(int statusCode, ResponseFactory responseFactory) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            String response = responseFactory.respond(new String(requestBytes, StandardCharsets.UTF_8));
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
    }

    private void startSseServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        AtomicReference<OutputStream> sseOutput = new AtomicReference<>();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch keepOpen = new CountDownLatch(1);
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream output = exchange.getResponseBody();
            sseOutput.set(output);
            writeSse(output, "endpoint", "/messages");
            connected.countDown();
            try {
                keepOpen.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        server.createContext("/messages", exchange -> {
            try {
                try {
                    assertThat(connected.await(2, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException(error);
                }
                Map<?, ?> request = objectMapper.readValue(exchange.getRequestBody(), Map.class);
                Object id = request.get("id");
                String method = String.valueOf(request.get("method"));
                if ("notifications/initialized".equals(method)) {
                    exchange.sendResponseHeaders(202, -1);
                    return;
                }
                Map<String, Object> response = switch (method) {
                    case "initialize" -> response(id, Map.of(
                            "protocolVersion", "2025-06-18",
                            "capabilities", Map.of("tools", Map.of("listChanged", false))));
                    case "tools/list" -> response(id, Map.of("tools", List.of(Map.of(
                            "name", "get_current_time",
                            "title", "Current time",
                            "description", "Return current time",
                            "inputSchema", Map.of("type", "object", "properties", Map.of())))));
                    case "tools/call" -> response(id, Map.of(
                            "content", List.of(Map.of("type", "text", "text", "12:00:00")),
                            "structuredContent", Map.of("time", "12:00:00"),
                            "isError", false));
                    default -> Map.of("jsonrpc", "2.0", "id", id,
                            "error", Map.of("code", -32601, "message", "method not found"));
                };
                writeSse(sseOutput.get(), "message", objectMapper.writeValueAsString(response));
                exchange.sendResponseHeaders(202, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private static Map<String, Object> response(Object id, Map<String, Object> result) {
        return Map.of("jsonrpc", "2.0", "id", id, "result", result);
    }

    private static void writeSse(OutputStream output, String event, String data) throws IOException {
        output.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        output.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private String serverUrl() {
        return serverUrl("/mcp");
    }

    private String serverUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static McpProperties properties(String url, Map<String, String> headers) {
        McpProperties properties = new McpProperties();
        McpProperties.Server server = new McpProperties.Server();
        server.setServerId("local-time");
        server.setUrl(url);
        server.setRequestTimeout(Duration.ofSeconds(2));
        server.setHeaders(headers);
        properties.setServers(List.of(server));
        return properties;
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("time?")),
                Map.of());
    }
}
