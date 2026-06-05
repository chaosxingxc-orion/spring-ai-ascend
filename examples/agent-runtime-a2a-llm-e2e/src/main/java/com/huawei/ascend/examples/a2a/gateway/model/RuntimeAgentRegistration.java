package com.huawei.ascend.examples.a2a.gateway.model;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.a2aproject.sdk.spec.AgentCard;

public record RuntimeAgentRegistration(
        RuntimeInstanceId runtimeInstanceId,
        String tenantId,
        String agentId,
        AgentCard agentCard,
        URI a2aEndpoint,
        URI healthEndpoint,
        String version,
        Duration ttl,
        Map<String, Object> metadata) {

    public RuntimeAgentRegistration {
        runtimeInstanceId = Objects.requireNonNull(runtimeInstanceId, "runtimeInstanceId");
        tenantId = required(tenantId, "tenantId");
        agentId = required(agentId, "agentId");
        agentCard = Objects.requireNonNull(agentCard, "agentCard");
        a2aEndpoint = Objects.requireNonNull(a2aEndpoint, "a2aEndpoint");
        healthEndpoint = Objects.requireNonNull(healthEndpoint, "healthEndpoint");
        version = required(version, "version");
        ttl = positive(ttl, "ttl");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
