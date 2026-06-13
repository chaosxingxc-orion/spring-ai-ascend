package com.huawei.ascend.examples.a2a.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class Mem0RestMemoryProviderTest {
    private final List<String> paths = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchesAndSavesViaMem0OssRestShape() throws Exception {
        startServer("/search", "/memories");
        Mem0RestMemoryProvider provider = new Mem0RestMemoryProvider(baseUrl(), "", false, "oss");
        AgentExecutionContext context = context();

        List<MemoryProvider.MemoryHit> hits = provider.search(context, "green tea", 3);
        provider.save(context, List.of(new MemoryProvider.MemoryRecord(null, "assistant",
                "the user likes green tea", Map.of())));

        assertThat(hits)
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.content()).isEqualTo("user likes green tea");
                    assertThat(hit.score()).isEqualTo(0.91);
                });
        assertThat(paths).containsExactly("/search", "/memories");
        assertThat(bodies.get(0))
                .contains("\"query\":\"green tea\"")
                .contains("\"top_k\":3")
                .contains("\"user_id\":\"user\"")
                .contains("\"agent_id\":\"agent\"");
        assertThat(bodies.get(1))
                .contains("\"infer\":false")
                .contains("\"role\":\"assistant\"")
                .contains("\"content\":\"the user likes green tea\"");
    }

    @Test
    void canUseMem0PlatformV3RestShapeWhenConfigured() throws Exception {
        startServer("/v3/memories/search/", "/v3/memories/add/");
        Mem0RestMemoryProvider provider = new Mem0RestMemoryProvider(baseUrl(), "secret", false, "platform");
        AgentExecutionContext context = context();

        provider.search(context, "green tea", 3);
        provider.save(context, List.of(new MemoryProvider.MemoryRecord(null, "assistant",
                "the user likes green tea", Map.of())));

        assertThat(paths).containsExactly("/v3/memories/search/", "/v3/memories/add/");
        assertThat(bodies.get(0))
                .contains("\"filters\":")
                .contains("\"user_id\":\"user\"")
                .contains("\"agent_id\":\"agent\"");
    }

    private void startServer(String searchPath, String addPath) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(searchPath, exchange -> respond(exchange,
                "{\"results\":[{\"id\":\"m1\",\"memory\":\"user likes green tea\",\"score\":0.91}]}"));
        server.createContext(addPath, exchange -> respond(exchange, "{\"results\":[]}"));
        server.start();
    }

    private void respond(HttpExchange exchange, String response) throws IOException {
        paths.add(exchange.getRequestURI().getPath());
        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE", List.of(RuntimeMessage.user("ping")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "state"));
    }
}
