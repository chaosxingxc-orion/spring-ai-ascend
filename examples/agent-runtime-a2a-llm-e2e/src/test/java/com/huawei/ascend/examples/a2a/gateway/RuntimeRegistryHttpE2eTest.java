package com.huawei.ascend.examples.a2a.gateway;

import com.huawei.ascend.examples.a2a.gateway.config.RuntimeRegistryConfiguration;
import com.huawei.ascend.examples.a2a.gateway.http.RouteGrantController.ResolveGrantRequest;
import com.huawei.ascend.examples.a2a.gateway.http.RuntimeRegistryController.RuntimeLeaseRenewalRequest;
import com.huawei.ascend.examples.a2a.gateway.http.RuntimeRegistryController.RuntimeRegistrationRequest;
import com.huawei.ascend.examples.a2a.gateway.model.AgentInteractionEvent;
import com.huawei.ascend.examples.a2a.gateway.model.InboundA2aContext;
import com.huawei.ascend.examples.a2a.gateway.model.RoutingContext;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeCapacitySnapshot;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeState;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = RuntimeRegistryHttpE2eTest.TestServiceFacade.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude="
                + "com.huawei.ascend.runtime.session.SessionManageConfiguration,"
                + "com.huawei.ascend.runtime.queue.QueueAutoConfiguration,"
                + "com.huawei.ascend.runtime.control.TaskControlAutoConfiguration,"
                + "com.huawei.ascend.runtime.app.RuntimeWiringConfiguration,"
                + "com.huawei.ascend.runtime.access.AccessLayerConfiguration,"
                + "com.huawei.ascend.runtime.engine.EngineAutoConfiguration")
class RuntimeRegistryHttpE2eTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void httpFacadeRoutesMultiTurnSessionAndDifferentAgents() {
        String tenant = "tenant-http-multiturn";
        register(tenant, "runtime-weather-1", "weather-agent", "http://runtime-weather-1.local/a2a");
        register(tenant, "runtime-travel-1", "travel-agent", "http://runtime-travel-1.local/a2a");

        JsonNode firstTurn = resolve(tenant, "weather-agent", "session-1", "corr-1", "ping");
        JsonNode secondTurn = resolve(tenant, "weather-agent", "session-1", "corr-2", "pong");
        JsonNode differentAgent = resolve(tenant, "travel-agent", "session-1", "corr-3", "book ticket");
        HttpJsonResponse cardResponse = get("/v1/agents/weather-agent/card?tenantId=" + tenant);
        HttpJsonResponse agentsResponse = get("/v1/agents?tenantId=" + tenant);

        assertThat(firstTurn.path("a2aEndpoint").asText()).isEqualTo("http://runtime-weather-1.local/a2a");
        assertThat(secondTurn.path("a2aEndpoint").asText()).isEqualTo("http://runtime-weather-1.local/a2a");
        assertThat(firstTurn.path("runtimeInstanceId").path("value").asText()).isEqualTo("runtime-weather-1");
        assertThat(secondTurn.path("runtimeInstanceId").path("value").asText()).isEqualTo("runtime-weather-1");
        assertThat(differentAgent.path("a2aEndpoint").asText()).isEqualTo("http://runtime-travel-1.local/a2a");
        assertThat(cardResponse.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(cardResponse.body().path("name").asText()).isEqualTo("weather-agent");
        assertThat(agentsResponse.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(agentsResponse.body()).hasSize(2);
    }

    @Test
    void httpFacadeStopsRoutingRuntimeThatIsAtCapacity() {
        String tenant = "tenant-http-capacity";
        register(tenant, "runtime-weather-2", "weather-agent-capacity", "http://runtime-weather-2.local/a2a");

        put(
                "/v1/runtime-registrations/runtime-weather-2/lease",
                new RuntimeLeaseRenewalRequest(
                        RuntimeState.AT_CAPACITY,
                        30,
                        null,
                        Map.of("reason", "load-test")));

        HttpJsonResponse response = post(
                "/v1/agents/weather-agent-capacity/routes/resolve?tenantId=" + tenant,
                new RoutingContext("session-capacity", "corr-capacity", Map.of("message", "ping")));

        assertThat(response.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.body().path("code").asText()).isEqualTo("RUNTIME_AT_CAPACITY");
    }

    @Test
    void httpFacadeStopsRoutingRuntimeWhoseLlmCapacitySnapshotIsFull() {
        String tenant = "tenant-http-llm-capacity";
        register(tenant, "runtime-weather-llm", "weather-agent-llm", "http://runtime-weather-llm.local/a2a");

        put(
                "/v1/runtime-registrations/runtime-weather-llm/lease",
                new RuntimeLeaseRenewalRequest(
                        RuntimeState.READY,
                        30,
                        null,
                        new RuntimeCapacitySnapshot(
                                0,
                                0,
                                0,
                                4,
                                0,
                                4,
                                80,
                                180,
                                450,
                                0,
                                0,
                                1.0,
                                Instant.parse("2026-06-08T00:00:00Z")),
                        Map.of("reason", "llm-saturated")));

        HttpJsonResponse response = post(
                "/v1/agents/weather-agent-llm/routes/resolve?tenantId=" + tenant,
                new RoutingContext("session-llm-capacity", "corr-llm-capacity", Map.of("message", "ping")));

        assertThat(response.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.body().path("code").asText()).isEqualTo("RUNTIME_AT_CAPACITY");
    }

    @Test
    void httpFacadeRouteResponseCarriesRuntimeCapacitySnapshot() {
        String tenant = "tenant-http-capacity-route";
        register(tenant, "runtime-weather-route", "weather-agent-route", "http://runtime-weather-route.local/a2a");

        put(
                "/v1/runtime-registrations/runtime-weather-route/lease",
                new RuntimeLeaseRenewalRequest(
                        RuntimeState.READY,
                        30,
                        null,
                        new RuntimeCapacitySnapshot(
                                1,
                                0,
                                8,
                                2,
                                0,
                                8,
                                40,
                                100,
                                250,
                                0,
                                0,
                                0.25,
                                Instant.parse("2026-06-08T00:00:01Z")),
                        Map.of("reason", "healthy")));

        JsonNode response = resolve(tenant, "weather-agent-route", "session-route", "corr-route", "ping");

        assertThat(response.path("capacitySnapshot").path("llmInFlight").asInt()).isEqualTo(2);
        assertThat(response.path("capacitySnapshot").path("p95FirstTokenMs").asLong()).isEqualTo(100);
    }

    @Test
    void gatewayFacadeForwardsA2aRequestToResolvedRuntime() throws IOException {
        String tenant = "tenant-http-gateway";
        AtomicReference<String> forwardedBody = new AtomicReference<>();
        AtomicReference<String> forwardedRuntime = new AtomicReference<>();
        AtomicReference<String> forwardedGrantId = new AtomicReference<>();
        HttpServer runtime = HttpServer.create(new InetSocketAddress(0), 0);
        runtime.createContext("/a2a", exchange -> {
            forwardedBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            forwardedRuntime.set(exchange.getRequestHeaders().getFirst("X-Agent-Examples-Runtime-Instance"));
            forwardedGrantId.set(exchange.getRequestHeaders().getFirst("X-Agent-Examples-Route-Grant-Id"));
            byte[] response = "{\"jsonrpc\":\"2.0\",\"id\":\"req-1\",\"result\":{\"ok\":true}}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(HttpStatus.OK.value(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        runtime.start();
        try {
            String endpoint = "http://localhost:" + runtime.getAddress().getPort() + "/a2a";
            register(tenant, "runtime-gateway-1", "gateway-agent", endpoint);

            HttpJsonResponse response = post(
                    "/v1/agents/gateway-agent/a2a?tenantId=" + tenant
                            + "&sessionId=session-gateway&correlationId=corr-gateway",
                    Map.of("jsonrpc", "2.0", "id", "req-1", "method", "message/send"));

            assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
            assertThat(response.body().path("result").path("ok").asBoolean()).isTrue();
            assertThat(forwardedBody.get()).contains("\"method\":\"message/send\"");
            assertThat(forwardedRuntime.get()).isEqualTo("runtime-gateway-1");
            assertThat(forwardedGrantId.get()).isNotBlank();
            assertThat(response.firstHeader("X-Agent-Examples-Runtime-Instance")).isEqualTo("runtime-gateway-1");
            assertThat(response.firstHeader("X-Agent-Examples-Route-Grant-Id")).isEqualTo(forwardedGrantId.get());
            assertThat(response.firstHeader("X-Agent-Examples-Route-Resolve-Ms")).isNotBlank();
            assertThat(response.firstHeader("X-Agent-Examples-First-Byte-Ms")).isNotBlank();
            assertThat(response.firstHeader("X-Agent-Examples-Forward-Start-Ms")).isNotBlank();
            HttpJsonResponse telemetry = get("/v1/a2a-interactions?tenantId=" + tenant
                    + "&correlationId=corr-gateway&limit=10");
            assertThat(telemetry.status()).isEqualTo(HttpStatus.OK.value());
            assertThat(telemetry.body()).hasSize(1);
            assertThat(telemetry.body().get(0).path("grantId").asText()).isEqualTo(forwardedGrantId.get());
        } finally {
            runtime.stop(0);
        }
    }

    @Test
    void httpFacadeIssuesValidRouteGrantAndRecordsA2aTelemetry() {
        String tenant = "tenant-http-grant";
        register(tenant, "runtime-target-1", "target-agent", "http://runtime-target-1.local/a2a");

        HttpJsonResponse grantResponse = post(
                "/v1/route-grants/resolve",
                new ResolveGrantRequest(
                        tenant,
                        "source-agent",
                        "target-agent",
                        "message/stream",
                        new RoutingContext("session-grant", "corr-grant", Map.of("reason", "east-west")),
                        30,
                        Map.of("client", "runtime-a")));

        assertThat(grantResponse.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(grantResponse.body().path("grantId").asText()).isNotBlank();
        assertThat(grantResponse.body().path("tenantId").asText()).isEqualTo(tenant);
        assertThat(grantResponse.body().path("targetRuntimeId").path("value").asText()).isEqualTo("runtime-target-1");
        assertThat(grantResponse.body().path("signature").asText()).isNotBlank();

        HttpJsonResponse validationResponse = post(
                "/v1/route-grants/validate",
                Map.of(
                        "grant", grantResponse.body(),
                        "context", new InboundA2aContext(
                                tenant,
                                "source-agent",
                                "target-agent",
                                "message/stream")));

        assertThat(validationResponse.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(validationResponse.body().path("accepted").asBoolean()).isTrue();

        HttpJsonResponse recordResponse = post(
                "/v1/a2a-interactions",
                new AgentInteractionEvent(
                        "event-grant-1",
                        "A2A_CALL_FINISHED",
                        Instant.parse("2026-06-05T00:00:00Z"),
                        tenant,
                        "runtime-source-1",
                        "source-agent",
                        "runtime-target-1",
                        "target-agent",
                        "session-grant",
                        "task-grant",
                        "corr-grant",
                        "trace-grant",
                        grantResponse.body().path("grantId").asText(),
                        "message/stream",
                        "OK",
                        3,
                        24,
                        81,
                        128,
                        256,
                        null,
                        "sha256:sample",
                        null,
                        Map.of("sample", true)));
        HttpJsonResponse queryResponse = get("/v1/a2a-interactions?tenantId=" + tenant + "&correlationId=corr-grant&limit=10");
        HttpJsonResponse healthResponse = get("/v1/gateway-health");

        assertThat(recordResponse.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(recordResponse.body().path("grantId").asText())
                .isEqualTo(grantResponse.body().path("grantId").asText());
        assertThat(queryResponse.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(queryResponse.body()).hasSize(1);
        assertThat(queryResponse.body().get(0).path("eventType").asText()).isEqualTo("A2A_CALL_FINISHED");
        assertThat(healthResponse.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(healthResponse.body().path("registeredRuntimeCount").asInt()).isGreaterThan(0);
        assertThat(healthResponse.body().path("telemetryEventCount").asLong()).isGreaterThan(0);
    }

    private void register(String tenant, String runtimeId, String agentId, String endpoint) {
        HttpJsonResponse response = post(
                "/v1/runtime-registrations",
                new RuntimeRegistrationRequest(
                        runtimeId,
                        tenant,
                        agentId,
                        agentCard(agentId),
                        URI.create(endpoint),
                        URI.create(endpoint.replace("/a2a", "/v1/health")),
                        "1.0.0",
                        Duration.ofSeconds(30).toSeconds(),
                        Map.of("zone", "az-1")));

        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body().path("runtimeInstanceId").path("value").asText()).isEqualTo(runtimeId);
    }

    private JsonNode resolve(String tenant, String agentId, String sessionId, String correlationId, String message) {
        HttpJsonResponse response = post(
                "/v1/agents/" + agentId + "/routes/resolve?tenantId=" + tenant,
                new RoutingContext(sessionId, correlationId, Map.of("message", message)));

        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        return response.body();
    }

    private HttpJsonResponse get(String path) {
        return exchange(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    private HttpJsonResponse post(String path, Object body) {
        return exchange(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json(body)))
                .build());
    }

    private HttpJsonResponse put(String path, Object body) {
        return exchange(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json(body)))
                .build());
    }

    private HttpJsonResponse exchange(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpJsonResponse(response.statusCode(), response.headers().map(), objectMapper.readTree(response.body()));
        } catch (java.io.IOException ex) {
            throw new AssertionError("HTTP request failed: " + request.uri(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("HTTP request interrupted: " + request.uri(), ex);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String json(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (RuntimeException ex) {
            throw new AssertionError("Failed to serialize request body", ex);
        }
    }

    private static AgentCard agentCard(String agentId) {
        return AgentCard.builder()
                .name(agentId)
                .description(agentId + " runtime")
                .url("/a2a")
                .version("1.0.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:8080"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .extendedAgentCard(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.JSONRPC.asString(),
                        "/a2a")))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(RuntimeRegistryConfiguration.class)
    static class TestServiceFacade {
    }

    private record HttpJsonResponse(int status, Map<String, List<String>> headers, JsonNode body) {

        private String firstHeader(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse("");
        }
    }
}
