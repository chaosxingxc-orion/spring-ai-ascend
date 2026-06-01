package com.huawei.ascend.service.access.protocol.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.service.access.temp.TemporaryA2aApplication;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        classes = TemporaryA2aApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class A2aPushNotificationModeTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Test
    void sendMessageWithPushNotificationConfigPostsA2aEventsToCallbackUrl() throws Exception {
        List<String> callbacks = new CopyOnWriteArrayList<>();
        HttpServer callbackServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        callbackServer.createContext("/callback", exchange -> {
            callbacks.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        callbackServer.start();
        try {
            String callbackUrl = "http://localhost:" + callbackServer.getAddress().getPort() + "/callback";
            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/a2a/"))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(pushRequestBody(callbackUrl)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = JSON.readTree(response.body());
            assertThat(body.path("error").isMissingNode()).isTrue();
            assertThat(body.path("result").path("taskId").asText()).startsWith("temporary-task-");

            waitUntilAtLeast(callbacks, 2);
            assertThat(callbacks).anySatisfy(callback -> assertThat(callback).contains("temporary task accepted"));
            assertThat(callbacks).anySatisfy(callback -> assertThat(callback).contains("temporary streaming result"));
        } finally {
            callbackServer.stop(0);
        }
    }

    private static String pushRequestBody(String callbackUrl) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "req-push",
                  "method": "SendMessage",
                  "params": {
                    "tenant": "tenant-001",
                    "configuration": {
                      "taskPushNotificationConfig": {
                        "id": "push-config-001",
                        "url": "%s",
                        "token": "test-token"
                      }
                    },
                    "message": {
                      "role": "ROLE_USER",
                      "messageId": "msg-push",
                      "contextId": "session-push",
                      "parts": [
                        {
                          "kind": "text",
                          "text": "hello from push mode"
                        }
                      ],
                      "metadata": {
                        "tenantId": "tenant-001",
                        "userId": "user-001",
                        "agentId": "agent-001",
                        "sessionId": "session-push",
                        "idempotencyKey": "idem-push",
                        "correlationId": "corr-push"
                      }
                    }
                  }
                }
                """.formatted(callbackUrl);
    }

    private static void waitUntilAtLeast(List<String> callbacks, int expectedSize) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline && callbacks.size() < expectedSize) {
            Thread.sleep(50L);
        }
        assertThat(callbacks).hasSizeGreaterThanOrEqualTo(expectedSize);
    }
}
