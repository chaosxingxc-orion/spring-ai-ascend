package com.huawei.ascend.agentsdk.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.agentsdk.spec.tool.HttpExecutionHandle;
import com.huawei.ascend.agentsdk.support.ToolExecutionException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HttpToolExecutorTest {

    private static HttpServer server;
    private static final AtomicReference<String> lastBody = new AtomicReference<>();
    private static final AtomicReference<String> lastQuery = new AtomicReference<>();
    private static final AtomicReference<String> lastContentType = new AtomicReference<>();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo-json", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] payload = "{\"status\":\"ok\",\"count\":2}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.createContext("/text", exchange -> {
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] payload = "plain answer".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.createContext("/fail", exchange -> {
            byte[] payload = "upstream exploded".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void postSendsInputsAsJsonAndDecodesJsonResponse() {
        HttpExecutionHandle handle = new HttpExecutionHandle(
                uri("/echo-json"), "POST", Map.of(), Duration.ofSeconds(5));

        Object result = new HttpToolExecutor().execute(handle, Map.of("orderId", "o-9"));

        assertThat(lastBody.get()).contains("\"orderId\":\"o-9\"");
        assertThat(lastContentType.get()).isEqualTo("application/json");
        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> decoded = (Map<?, ?>) result;
        assertThat(decoded.get("status")).isEqualTo("ok");
        assertThat(decoded.get("count")).isEqualTo(2);
    }

    @Test
    void getCarriesInputsAsQueryParametersAndReturnsTextBody() {
        HttpExecutionHandle handle = new HttpExecutionHandle(
                uri("/text"), "GET", Map.of(), Duration.ofSeconds(5));

        Object result = new HttpToolExecutor().execute(handle, Map.of("q", "wealth advice"));

        assertThat(lastQuery.get()).isEqualTo("q=wealth+advice");
        assertThat(result).isEqualTo("plain answer");
    }

    @Test
    void nonSuccessStatusFailsLoudlyWithStatusAndBodyPreview() {
        HttpExecutionHandle handle = new HttpExecutionHandle(
                uri("/fail"), "POST", Map.of(), Duration.ofSeconds(5));

        assertThatThrownBy(() -> new HttpToolExecutor().execute(handle, Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("502")
                .hasMessageContaining("upstream exploded");
    }

    @Test
    void connectionFailureSurfacesAsToolExecutionException() {
        HttpExecutionHandle handle = new HttpExecutionHandle(
                URI.create("http://127.0.0.1:1/unreachable"), "POST", Map.of(), Duration.ofSeconds(2));

        assertThatThrownBy(() -> new HttpToolExecutor().execute(handle, Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("request failed");
    }

    private static URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
