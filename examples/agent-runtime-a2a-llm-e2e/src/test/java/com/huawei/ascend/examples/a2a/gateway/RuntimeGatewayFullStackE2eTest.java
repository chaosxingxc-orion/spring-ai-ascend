package com.huawei.ascend.examples.a2a.gateway;

import com.huawei.ascend.examples.a2a.gateway.config.RuntimeRegistryConfiguration;
import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentDriver;
import com.huawei.ascend.runtime.engine.spi.AgentDriver;
import com.huawei.ascend.runtime.engine.spi.OutputConverter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeGatewayFullStackE2eTest {

    private static final String TENANT = "tenant-full-stack";
    private static final String AGENT = "full-stack-ping-agent";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void gatewayFacadeForwardsA2aRequestToRealRuntimeApplication() {
        try (ConfigurableApplicationContext runtime = startRuntime();
                ConfigurableApplicationContext gateway = startGateway()) {
            int runtimePort = localPort(runtime);
            int gatewayPort = localPort(gateway);
            String runtimeBase = "http://localhost:" + runtimePort;

            String agentCard = rawGet(runtimeBase + "/.well-known/agent-card.json");
            registerRuntime(gatewayPort, runtimeBase, agentCard);

            RawHttpResponse response = postRaw(
                    "http://localhost:" + gatewayPort + "/v1/agents/" + AGENT
                            + "/a2a?tenantId=" + TENANT + "&sessionId=session-full&correlationId=corr-full",
                    streamingRequest());

            assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
            assertThat(response.firstHeader("content-type")).contains("text/event-stream");
            assertThat(response.firstHeader("X-Agent-Examples-Runtime-Instance")).isEqualTo("runtime-full-stack-1");
            assertThat(response.firstHeader("X-Agent-Examples-Route-Resolve-Ms")).isNotBlank();
            assertThat(response.firstHeader("X-Agent-Examples-First-Byte-Ms")).isNotBlank();
            assertThat(response.firstHeader("X-Agent-Examples-Forward-Ms")).isNotBlank();
            assertThat(response.body()).contains("pong");
            assertThat(response.body()).contains("\"runStatus\":\"completed\"");
        }
    }

    private ConfigurableApplicationContext startRuntime() {
        return new SpringApplicationBuilder(TestRuntimeApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--agent-runtime.access.a2a.default-tenant-id=" + TENANT,
                        "--agent-runtime.access.a2a.default-agent-id=" + AGENT);
    }

    private ConfigurableApplicationContext startGateway() {
        return new SpringApplicationBuilder(TestGatewayApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0");
    }

    private void registerRuntime(int gatewayPort, String runtimeBase, String agentCard) {
        String body = """
                {
                  "runtimeInstanceId": "runtime-full-stack-1",
                  "tenantId": "%s",
                  "agentId": "%s",
                  "agentCard": %s,
                  "a2aEndpoint": "%s/a2a",
                  "healthEndpoint": "%s/v1/health",
                  "version": "1.0.0",
                  "ttlSeconds": 30,
                  "metadata": {
                    "zone": "az-1"
                  }
                }
                """.formatted(TENANT, AGENT, agentCard, runtimeBase, runtimeBase);
        RawHttpResponse response = postRaw("http://localhost:" + gatewayPort + "/v1/runtime-registrations", body);
        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.path("runtimeInstanceId").path("value").asText()).isEqualTo("runtime-full-stack-1");
    }

    private String streamingRequest() {
        String sessionId = "session-" + UUID.randomUUID();
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "full-stack-req",
                  "method": "message/stream",
                  "params": {
                    "message": {
                      "messageId": "msg-full-stack",
                      "contextId": "%s",
                      "metadata": {
                        "tenantId": "%s",
                        "userId": "full-stack-user",
                        "agentId": "%s",
                        "sessionId": "%s"
                      },
                      "parts": [
                        {
                          "kind": "text",
                          "text": "ping"
                        }
                      ]
                    }
                  }
                }
                """.formatted(sessionId, TENANT, AGENT, sessionId);
    }

    private String rawGet(String uri) {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(uri))
                            .timeout(Duration.ofSeconds(20))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
            return response.body();
        } catch (java.io.IOException ex) {
            throw new AssertionError("HTTP GET failed: " + uri, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("HTTP GET interrupted: " + uri, ex);
        }
    }

    private RawHttpResponse postRaw(String uri, String body) {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(uri))
                            .timeout(Duration.ofSeconds(20))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return new RawHttpResponse(response.statusCode(), response.headers().map(), response.body());
        } catch (java.io.IOException ex) {
            throw new AssertionError("HTTP POST failed: " + uri, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("HTTP POST interrupted: " + uri, ex);
        }
    }

    private int localPort(ConfigurableApplicationContext context) {
        Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        assertThat(port).isNotNull();
        return port;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestRuntimeApplication {

        @Bean
        AgentDriver fullStackPingAgentDriver() {
            return new AbstractAgentDriver() {
                @Override
                public String name() {
                    return AGENT;
                }

                @Override
                public String frameworkId() {
                    return "stub";
                }

                @Override
                public Object invoke(InvocationRequest request) {
                    // Wait so the SSE subscription is established before the (synchronous) output flows.
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted before producing the test response", ex);
                    }
                    return List.of("pong");
                }

                @Override
                public OutputConverter outputConverter() {
                    return frameworkStream -> {
                        List<?> items = frameworkStream instanceof List<?> list ? list : List.of();
                        String text = items.isEmpty() ? "" : String.valueOf(items.get(0));
                        return syncPublisher(List.of(RunEvent.accepted(), RunEvent.completed(1, text)));
                    };
                }
            };
        }

        private static Flow.Publisher<RunEvent> syncPublisher(List<RunEvent> items) {
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                private int idx = 0;
                private boolean cancelled = false;

                @Override
                public void request(long n) {
                    while (n-- > 0 && idx < items.size() && !cancelled) {
                        subscriber.onNext(items.get(idx++));
                    }
                    if (idx >= items.size() && !cancelled) {
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(RuntimeRegistryConfiguration.class)
    static class TestGatewayApplication {
    }

    private record RawHttpResponse(int status, Map<String, java.util.List<String>> headers, String body) {

        private String firstHeader(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse("");
        }
    }
}
