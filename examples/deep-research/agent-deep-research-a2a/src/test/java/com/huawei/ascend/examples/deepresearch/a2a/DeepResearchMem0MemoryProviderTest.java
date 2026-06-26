/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

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

class DeepResearchMem0MemoryProviderTest {

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
        DeepResearchMem0MemoryProvider provider = new DeepResearchMem0MemoryProvider(baseUrl(), "", false);
        AgentExecutionContext context = context();

        List<MemoryProvider.MemoryHit> hits = provider.search(context, "上下文窗口 DeepSeek", 3);
        provider.save(context, List.of(new MemoryProvider.MemoryRecord(
                null, "assistant", "对比结论：上下文窗口最大的是 DeepSeek 128K", Map.of())));

        assertThat(hits)
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.content()).isEqualTo("上下文窗口最大的是 DeepSeek 128K");
                    assertThat(hit.score()).isEqualTo(0.88);
                });
        assertThat(paths).containsExactly("/search", "/memories");
        assertThat(bodies.get(0))
                .contains("\"query\":\"上下文窗口 DeepSeek\"")
                .contains("\"top_k\":3")
                .contains("\"user_id\":\"user\"")
                .contains("\"agent_id\":\"deep-research-agent\"");
        assertThat(bodies.get(1))
                .contains("\"infer\":false")
                .contains("\"role\":\"assistant\"")
                .contains("DeepSeek 128K");
    }

    private void startServer(String searchPath, String addPath) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(searchPath, exchange -> respond(exchange,
                "{\"results\":[{\"id\":\"m1\",\"memory\":\"上下文窗口最大的是 DeepSeek 128K\",\"score\":0.88}]}"));
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
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "deep-research-agent"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("ping")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "state"));
    }
}
