package com.huawei.ascend.examples.a2a.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider.MemoryHit;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider.MemoryRecord;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JiuwenMemoryProviderTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> receivedBodies = new ArrayList<>();
    private final List<String> receivedPaths = new ArrayList<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                receivedPaths.add(path);
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                receivedBodies.add(body);
                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ─── search ──────────────────────────────────────────────────────

    @Test
    void searchParsesResultsFromJsonResponse() {
        responseBody = """
                {"results":[
                    {"mem_id":"m1","content":"user likes green tea","score":0.85,"type":"personal"},
                    {"mem_id":"m2","content":"prefers morning meetings","score":0.72}
                ]}
                """;
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        List<MemoryHit> hits = provider.search(context(), "green tea", 5);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).id()).isEqualTo("m1");
        assertThat(hits.get(0).content()).isEqualTo("user likes green tea");
        assertThat(hits.get(0).score()).isEqualTo(0.85);
        assertThat(hits.get(0).metadata()).containsEntry("type", "personal");
        assertThat(hits.get(1).id()).isEqualTo("m2");
        assertThat(hits.get(1).content()).isEqualTo("prefers morning meetings");
        assertThat(hits.get(1).score()).isEqualTo(0.72);
        assertThat(hits.get(1).metadata()).doesNotContainKey("type");
    }

    @Test
    void searchReturnsEmptyWhenResponseHasNoResultsKey() {
        responseBody = "{\"data\":[]}";
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        assertThat(provider.search(context(), "query", 5)).isEmpty();
    }

    @Test
    void searchSendsCorrectUserIdAndScopeId() {
        responseBody = "{\"results\":[]}";
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        provider.search(context("user-A", "tenant-X"), "green tea", 3);

        assertThat(receivedPaths).last().isEqualTo("/search_memory/");
        String body = receivedBodies.get(receivedBodies.size() - 1);
        assertThat(body).contains("\"user_id\":\"user-A\"");
        assertThat(body).contains("\"scope_id\":\"user-A\"");
        assertThat(body).contains("\"query\":\"green tea\"");
        assertThat(body).contains("\"num\":3");
        assertThat(body).contains("\"threshold\":0.3");
    }

    @Test
    void searchReturnsEmptyOnHttpError() {
        responseStatus = 500;
        responseBody = "Internal Server Error";
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        assertThat(provider.search(context(), "query", 5)).isEmpty();
    }

    @Test
    void searchScoreParsesStringValue() {
        responseBody = """
                {"results":[{"mem_id":"m1","content":"fact","score":"0.95"}]}
                """;
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        List<MemoryHit> hits = provider.search(context(), "fact", 5);

        assertThat(hits).singleElement().satisfies(hit ->
                assertThat(hit.score()).isEqualTo(0.95));
    }

    @Test
    void searchScoreDefaultsToZeroWhenUnparseable() {
        responseBody = """
                {"results":[{"mem_id":"m1","content":"fact","score":"not-a-number"}]}
                """;
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        List<MemoryHit> hits = provider.search(context(), "fact", 5);

        assertThat(hits).singleElement().satisfies(hit ->
                assertThat(hit.score()).isEqualTo(0.0));
    }

    // ─── save ────────────────────────────────────────────────────────

    @Test
    void savePostsMessagesWithCorrectUserId() {
        responseBody = "{\"ok\":true}";
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        provider.save(context("user-B", "tenant-Y"), List.of(
                new MemoryRecord(null, "user", "what is machine learning?", Map.of()),
                new MemoryRecord(null, "assistant", "ML is a subset of AI.", Map.of())));

        assertThat(receivedPaths).last().isEqualTo("/add_messages/");
        String body = receivedBodies.get(receivedBodies.size() - 1);
        assertThat(body).contains("\"user_id\":\"user-B\"");
        assertThat(body).contains("\"scope_id\":\"user-B\"");
        assertThat(body).contains("\"role\":\"user\"");
        assertThat(body).contains("\"content\":\"what is machine learning?\"");
        assertThat(body).contains("\"role\":\"assistant\"");
        assertThat(body).contains("\"content\":\"ML is a subset of AI.\"");
    }

    @Test
    void saveSkipsWhenRecordsIsNull() {
        responseBody = "{}";
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        provider.save(context(), null);

        assertThat(receivedPaths).isEmpty();
    }

    @Test
    void saveSkipsWhenRecordsIsEmpty() {
        responseBody = "{}";
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        provider.save(context(), List.of());

        assertThat(receivedPaths).isEmpty();
    }

    @Test
    void saveDoesNotThrowOnHttpError() {
        responseStatus = 503;
        responseBody = "Service Unavailable";
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        provider.save(context(), List.of(new MemoryRecord(null, "user", "hi", Map.of())));

        assertThat(receivedPaths).hasSize(1);
    }

    // ─── init ────────────────────────────────────────────────────────

    @Test
    void initHealthCheckHitsHealthEndpoint() {
        responseBody = "{\"status\":\"ok\"}";
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        provider.init(context());

        assertThat(receivedPaths).containsExactly("/health");
    }

    @Test
    void initDoesNotThrowOnNon200Status() {
        responseStatus = 503;
        responseBody = "Service Unavailable";
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        provider.init(context());

        assertThat(receivedPaths).containsExactly("/health");
    }

    // ─── connection failure ──────────────────────────────────────────

    @Test
    void searchReturnsEmptyOnConnectionFailure() {
        server.stop(0);
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        assertThat(provider.search(context(), "query", 5)).isEmpty();
    }

    @Test
    void saveDoesNotThrowOnConnectionFailure() {
        server.stop(0);
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        provider.save(context(), List.of(new MemoryRecord(null, "user", "hi", Map.of())));
        // no exception thrown
    }

    @Test
    void initDoesNotThrowOnConnectionFailure() {
        server.stop(0);
        JiuwenMemoryProvider provider = new JiuwenMemoryProvider(baseUrl);

        provider.init(context());
        // no exception thrown
    }

    // ─── helpers ─────────────────────────────────────────────────────

    private static AgentExecutionContext context() {
        return context("test-user", "test-tenant");
    }

    private static AgentExecutionContext context(String userId, String tenantId) {
        return new AgentExecutionContext(
                new RuntimeIdentity(tenantId, userId, "session", "task", "agent"),
                "USER_MESSAGE", List.of(RuntimeMessage.user("ping")), Map.of());
    }
}
