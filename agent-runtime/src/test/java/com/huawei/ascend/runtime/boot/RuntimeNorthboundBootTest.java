package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the smallest possible consumer shape — a Boot app that merely depends on
 * the jar, with no component scan of {@code runtime.boot} — and proves the
 * northbound surface is actually routed. Guards against the failure mode where
 * the engine wires up, health reports UP, and yet {@code /a2a} and the agent
 * card silently 404 because controller registration depended on scanning.
 */
@SpringBootTest(
        classes = RuntimeNorthboundBootTest.PureDependencyHost.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RuntimeNorthboundBootTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void agentCardIsServedWithoutComponentScanning() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/.well-known/agent-card.json"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\"");
    }

    @Test
    void a2aEndpointIsRoutedWithoutComponentScanning() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/a2a"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"jsonrpc\":\"2.0\",\"id\":\"probe-1\",\"method\":\"tasks/get\","
                                        + "\"params\":{\"id\":\"no-such-task\"}}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // Routed means anything but 404: an unknown task yields a JSON-RPC error
        // payload over HTTP 200, which proves the controller handled the call.
        assertThat(response.statusCode()).isNotEqualTo(404);
        assertThat(response.body()).contains("jsonrpc");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class PureDependencyHost {
    }
}
