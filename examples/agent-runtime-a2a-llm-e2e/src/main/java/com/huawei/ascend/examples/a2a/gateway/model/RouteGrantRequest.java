package com.huawei.ascend.examples.a2a.gateway.model;

import java.time.Duration;
import java.util.Objects;

public record RouteGrantRequest(
        String tenantId,
        String sourceAgentId,
        String targetAgentId,
        String a2aMethod,
        RoutingContext routingContext,
        Duration ttl) {

    public RouteGrantRequest {
        tenantId = required(tenantId, "tenantId");
        sourceAgentId = required(sourceAgentId, "sourceAgentId");
        targetAgentId = required(targetAgentId, "targetAgentId");
        a2aMethod = required(a2aMethod, "a2aMethod");
        routingContext = routingContext == null ? RoutingContext.empty() : routingContext;
        ttl = ttl == null ? Duration.ofMinutes(1) : ttl;
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
