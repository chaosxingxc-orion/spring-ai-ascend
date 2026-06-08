package com.huawei.ascend.examples.a2a.gateway.core;

import com.huawei.ascend.examples.a2a.gateway.api.AgentDiscoveryApi;
import com.huawei.ascend.examples.a2a.gateway.model.A2aGatewayForwardException;
import com.huawei.ascend.examples.a2a.gateway.model.A2aGatewayResponse;
import com.huawei.ascend.examples.a2a.gateway.model.A2aGatewayStreamResponse;
import com.huawei.ascend.examples.a2a.gateway.model.RoutingContext;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeRoute;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RuntimeA2aGateway {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "content-length",
            "expect",
            "host",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade");

    private final AgentDiscoveryApi discoveryApi;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public RuntimeA2aGateway(AgentDiscoveryApi discoveryApi) {
        this(discoveryApi, HttpClient.newHttpClient(), Duration.ofSeconds(30));
    }

    public RuntimeA2aGateway(AgentDiscoveryApi discoveryApi, HttpClient httpClient, Duration requestTimeout) {
        this.discoveryApi = Objects.requireNonNull(discoveryApi, "discoveryApi");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    public A2aGatewayResponse forward(
            String agentId,
            String tenantId,
            RoutingContext routingContext,
            byte[] body,
            Map<String, List<String>> requestHeaders) {
        Instant routeStart = Instant.now();
        RuntimeRoute route = discoveryApi.resolveRoute(
                agentId,
                tenantId,
                routingContext == null ? RoutingContext.empty() : routingContext);
        Duration routeResolveLatency = Duration.between(routeStart, Instant.now());
        HttpRequest request = buildForwardRequest(route, body, requestHeaders);
        try {
            Instant forwardStart = Instant.now();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            Duration firstByteLatency = Duration.between(forwardStart, Instant.now());
            byte[] responseBody;
            try (InputStream responseStream = response.body()) {
                responseBody = responseStream.readAllBytes();
            }
            Duration forwardLatency = Duration.between(forwardStart, Instant.now());
            return new A2aGatewayResponse(
                    response.statusCode(),
                    response.headers().firstValue("content-type").orElse("application/json"),
                    routeResolveLatency,
                    firstByteLatency,
                    forwardLatency,
                    route.runtimeInstanceId().value(),
                    responseBody);
        } catch (IOException ex) {
            throw new A2aGatewayForwardException("Failed to forward A2A request to " + route.a2aEndpoint(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new A2aGatewayForwardException("Interrupted while forwarding A2A request to " + route.a2aEndpoint(), ex);
        }
    }

    public A2aGatewayStreamResponse forwardStreaming(
            String agentId,
            String tenantId,
            RoutingContext routingContext,
            byte[] body,
            Map<String, List<String>> requestHeaders) {
        Instant routeStart = Instant.now();
        RuntimeRoute route = discoveryApi.resolveRoute(
                agentId,
                tenantId,
                routingContext == null ? RoutingContext.empty() : routingContext);
        Duration routeResolveLatency = Duration.between(routeStart, Instant.now());
        HttpRequest request = buildForwardRequest(route, body, requestHeaders);
        try {
            Instant forwardStart = Instant.now();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            Duration firstByteLatency = Duration.between(forwardStart, Instant.now());
            return new A2aGatewayStreamResponse(
                    response.statusCode(),
                    response.headers().firstValue("content-type").orElse("application/json"),
                    routeResolveLatency,
                    firstByteLatency,
                    route.runtimeInstanceId().value(),
                    response.body());
        } catch (IOException ex) {
            throw new A2aGatewayForwardException("Failed to forward A2A request to " + route.a2aEndpoint(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new A2aGatewayForwardException("Interrupted while forwarding A2A request to " + route.a2aEndpoint(), ex);
        }
    }

    private HttpRequest buildForwardRequest(
            RuntimeRoute route,
            byte[] body,
            Map<String, List<String>> requestHeaders) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(route.a2aEndpoint())
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body.clone()));
        copyHeaders(builder, requestHeaders);
        builder.header("X-Agent-Examples-Runtime-Instance", route.runtimeInstanceId().value());
        if (!containsHeader(requestHeaders, "content-type")) {
            builder.header("Content-Type", "application/json");
        }
        return builder.build();
    }

    private void copyHeaders(HttpRequest.Builder builder, Map<String, List<String>> requestHeaders) {
        if (requestHeaders == null) {
            return;
        }
        requestHeaders.forEach((name, values) -> {
            if (isForwardable(name) && values != null) {
                values.stream()
                        .filter(Objects::nonNull)
                        .forEach(value -> builder.header(name, value));
            }
        });
    }

    private boolean isForwardable(String name) {
        return name != null && !HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean containsHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) {
            return false;
        }
        return headers.keySet().stream()
                .filter(Objects::nonNull)
                .anyMatch(candidate -> candidate.equalsIgnoreCase(name));
    }
}
