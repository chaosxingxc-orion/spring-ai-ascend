package com.huawei.ascend.service.access.protocol.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.service.access.temp.TemporaryA2aApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        classes = TemporaryA2aApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class A2aSendMessageModeTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Test
    void sendMessageReturnsJsonRpcAcceptedMessage() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/a2a/"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody("SendMessage")))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(body.path("id").asText()).isEqualTo("req-send");
        assertThat(body.path("error").isMissingNode()).isTrue();
        assertThat(body.path("result").path("role").asText()).isEqualTo("ROLE_AGENT");
        assertThat(body.path("result").path("taskId").asText()).startsWith("temporary-task-");
        assertThat(body.path("result").path("parts").get(0).path("text").asText())
                .isEqualTo("Accepted by temporary L4 TaskHandler placeholder");
        assertThat(body.path("result").path("metadata").path("tenantId").asText()).isEqualTo("tenant-001");
    }

    static String requestBody(String method) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "req-send",
                  "method": "%s",
                  "params": {
                    "tenant": "tenant-001",
                    "message": {
                      "role": "ROLE_USER",
                      "messageId": "msg-send",
                      "contextId": "session-send",
                      "parts": [
                        {
                          "kind": "text",
                          "text": "hello from send mode"
                        }
                      ],
                      "metadata": {
                        "tenantId": "tenant-001",
                        "userId": "user-001",
                        "agentId": "agent-001",
                        "sessionId": "session-send",
                        "idempotencyKey": "idem-send",
                        "correlationId": "corr-send"
                      }
                    }
                  }
                }
                """.formatted(method);
    }
}
