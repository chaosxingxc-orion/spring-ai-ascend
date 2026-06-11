package com.huawei.ascend.examples.langgraph;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Connection settings for a remote LangGraph runtime (LangGraph Platform /
 * langgraph-api dev server). {@code assistantId} names the deployed graph;
 * {@code endpointPath} defaults to the stateless {@code /runs/stream} route.
 * Extra headers carry deployment auth (e.g. {@code X-Api-Key}).
 */
public final class LangGraphRuntimeClientProperties {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final URI baseUrl;
    private final String assistantId;
    private final String endpointPath;
    private final Map<String, String> headers;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    public LangGraphRuntimeClientProperties(String baseUrl, String assistantId) {
        this(baseUrl, assistantId, "/runs/stream", Map.of());
    }

    public LangGraphRuntimeClientProperties(
            String baseUrl, String assistantId, String endpointPath, Map<String, String> headers) {
        this(baseUrl, assistantId, endpointPath, headers, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    public LangGraphRuntimeClientProperties(
            String baseUrl, String assistantId, String endpointPath, Map<String, String> headers,
            Duration connectTimeout, Duration requestTimeout) {
        this.baseUrl = URI.create(requireNonBlank(baseUrl, "baseUrl"));
        this.assistantId = requireNonBlank(assistantId, "assistantId");
        this.endpointPath = normalizePath(endpointPath);
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
    }

    public URI endpoint() {
        String base = baseUrl.toString();
        if (base.endsWith("/") && endpointPath.startsWith("/")) {
            return URI.create(base.substring(0, base.length() - 1) + endpointPath);
        }
        if (!base.endsWith("/") && !endpointPath.startsWith("/")) {
            return URI.create(base + "/" + endpointPath);
        }
        return URI.create(base + endpointPath);
    }

    public String assistantId() {
        return assistantId;
    }

    public Map<String, String> headers() {
        return headers;
    }

    /** Bound on establishing the TCP connection; without it a black-holed upstream blocks forever. */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * Bound on receiving the response headers. The SSE body is streamed and is
     * not subject to this timeout; mid-stream hangs are handled by closing the
     * raw stream.
     */
    public Duration requestTimeout() {
        return requestTimeout;
    }

    private static String normalizePath(String path) {
        String value = path == null || path.isBlank() ? "/runs/stream" : path;
        return value.startsWith("/") ? value : "/" + value;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
