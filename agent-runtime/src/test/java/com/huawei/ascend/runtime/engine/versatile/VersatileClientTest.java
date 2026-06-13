package com.huawei.ascend.runtime.engine.versatile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies VersatileClient sends correct HTTP requests with proper body,
 * headers, and streams responses. Would have caught the HTTP-422 "body
 * missing" regression.
 */
class VersatileClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedContentType = new AtomicReference<>();
    private final AtomicReference<String> capturedMethod = new AtomicReference<>();
    private final AtomicReference<String> capturedUri = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<String> responseContentType =
            new AtomicReference<>("text/event-stream");

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedUri.set(exchange.getRequestURI().toString());
            capturedContentType.set(
                    exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            capturedBody.set(new String(bodyBytes, StandardCharsets.UTF_8));

            byte[] resp = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", responseContentType.get());
            exchange.sendResponseHeaders(responseStatus.get(), resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ── Happy path ──

    @Test
    void sendsPostWithJsonBodyAndContentType() {
        responseBody.set("data:{\"event\":\"message\",\"data\":{\"text\":\"hello\"}}\n\n"
                + "data:{\"event\":\"end\",\"data\":{}}\n\n");

        VersatileProperties props = propsForLocalServer();
        VersatileClient client = new VersatileClient(props);
        VersatileHttpRequest req = request(props, "/conversations/c001?ws=1",
                Map.of("content-type", "application/json", "stream", "true"),
                Map.of("inputs", Map.of("query", "预订酒店")));

        List<String> lines = client.stream(req).toList();

        assertThat(capturedMethod.get()).isEqualTo("POST");
        assertThat(capturedContentType.get()).isEqualTo("application/json");
        assertThat(capturedBody.get()).contains("\"inputs\"");
        assertThat(capturedBody.get()).contains("\"query\"");
        assertThat(capturedBody.get()).contains("预订酒店");
        assertThat(lines).contains(
                "data:{\"event\":\"message\",\"data\":{\"text\":\"hello\"}}",
                "data:{\"event\":\"end\",\"data\":{}}");
    }

    @Test
    void addsContentTypeWhenNotConfigured() {
        responseBody.set("data:{\"event\":\"end\",\"data\":{}}\n\n");

        VersatileProperties props = propsForLocalServer();
        VersatileClient client = new VersatileClient(props);
        VersatileHttpRequest req = request(props, "/",
                Map.of(), Map.of("inputs", Map.of("query", "hi")));

        client.stream(req).toList();

        assertThat(capturedContentType.get()).isEqualTo("application/json");
        assertThat(capturedBody.get()).contains("\"query\"");
        assertThat(capturedBody.get()).contains("hi");
    }

    @Test
    void streamsSseLinesToStream() {
        responseBody.set("data:{\"event\":\"message\",\"data\":{\"text\":\"chunk1\"}}\n\n"
                + "data:{\"event\":\"message\",\"data\":{\"text\":\"chunk2\"}}\n\n"
                + "data:{\"event\":\"end\",\"data\":{}}\n\n");

        VersatileProperties props = propsForLocalServer();
        VersatileClient client = new VersatileClient(props);
        VersatileHttpRequest req = request(props, "/",
                Map.of("content-type", "application/json"),
                Map.of("inputs", Map.of("query", "test")));

        List<String> lines = client.stream(req).toList();

        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).contains("\"text\":\"chunk1\"");
        assertThat(lines.get(1)).contains("\"text\":\"chunk2\"");
        assertThat(lines.get(2)).contains("\"event\":\"end\"");
    }

    @Test
    void filtersEmptyLines() {
        responseBody.set("\n\ndata:{\"event\":\"end\",\"data\":{}}\n\n\n");

        VersatileProperties props = propsForLocalServer();
        VersatileClient client = new VersatileClient(props);
        VersatileHttpRequest req = request(props, "/",
                Map.of("content-type", "application/json"),
                Map.of("inputs", Map.of("query", "test")));

        List<String> lines = client.stream(req).toList();
        assertThat(lines).hasSize(1);
    }

    // ── Error paths ──

    @Test
    void throwsOnHttpErrorStatus() {
        responseStatus.set(500);
        responseBody.set("Internal Server Error");

        VersatileProperties props = propsForLocalServer();
        VersatileClient client = new VersatileClient(props);
        VersatileHttpRequest req = request(props, "/",
                Map.of("content-type", "application/json"),
                Map.of("inputs", Map.of("query", "test")));

        Stream<String> stream = client.stream(req);
        assertThatThrownBy(() -> stream.toList())
                .isInstanceOf(VersatileClient.VersatileClientException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void throwsOnConnectionRefused() {
        VersatileProperties props = new VersatileProperties();
        props.setUrl("http://127.0.0.1:1");
        props.setTimeout(Duration.ofSeconds(2));

        VersatileClient client = new VersatileClient(props);
        VersatileHttpRequest req = new VersatileHttpRequest(
                "POST", props.resolveUrl("test-conv"),
                Map.of("content-type", "application/json"),
                Map.of("inputs", Map.of("query", "test")));

        Stream<String> stream = client.stream(req);
        assertThatThrownBy(() -> stream.toList())
                .isInstanceOf(VersatileClient.VersatileClientException.class)
                .hasMessageContaining("versatile upstream error");
    }

    // ── Helpers ──

    private VersatileProperties propsForLocalServer() {
        VersatileProperties props = new VersatileProperties();
        props.setUrl("http://localhost:" + port + "/v1/test");
        props.setTimeout(Duration.ofSeconds(5));
        return props;
    }

    private VersatileHttpRequest request(VersatileProperties props, String suffix,
            Map<String, String> headers, Map<String, Object> body) {
        return new VersatileHttpRequest(
                "POST", props.resolveUrl("conv-001") + suffix, headers, body);
    }
}
