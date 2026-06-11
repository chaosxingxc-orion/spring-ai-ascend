package com.huawei.ascend.runtime.engine.agentscope;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public final class AgentScopeRuntimeClientProperties {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final URI baseUrl;
    private final String endpointPath;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    public AgentScopeRuntimeClientProperties(String baseUrl) {
        this(baseUrl, "/process");
    }

    public AgentScopeRuntimeClientProperties(String baseUrl, String endpointPath) {
        this(baseUrl, endpointPath, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    public AgentScopeRuntimeClientProperties(
            String baseUrl, String endpointPath, Duration connectTimeout, Duration requestTimeout) {
        this.baseUrl = URI.create(requireNonBlank(baseUrl, "baseUrl"));
        this.endpointPath = normalizePath(endpointPath);
        this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
    }

    /** Bound on establishing the TCP connection; without it a black-holed upstream blocks forever. */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * Bound on receiving the response headers. The SSE body is streamed and is
     * not subject to this timeout; mid-stream hangs are handled by closing the
     * raw stream (cancel-through).
     */
    public Duration requestTimeout() {
        return requestTimeout;
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

    private static String normalizePath(String path) {
        String value = path == null || path.isBlank() ? "/process" : path;
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
