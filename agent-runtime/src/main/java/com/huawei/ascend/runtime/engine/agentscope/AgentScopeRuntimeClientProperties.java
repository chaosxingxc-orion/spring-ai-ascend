package com.huawei.ascend.runtime.engine.agentscope;

import java.net.URI;
import java.util.Objects;

public final class AgentScopeRuntimeClientProperties {

    private final URI baseUrl;
    private final String endpointPath;

    public AgentScopeRuntimeClientProperties(String baseUrl) {
        this(baseUrl, "/process");
    }

    public AgentScopeRuntimeClientProperties(String baseUrl, String endpointPath) {
        this.baseUrl = URI.create(requireNonBlank(baseUrl, "baseUrl"));
        this.endpointPath = normalizePath(endpointPath);
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
}
