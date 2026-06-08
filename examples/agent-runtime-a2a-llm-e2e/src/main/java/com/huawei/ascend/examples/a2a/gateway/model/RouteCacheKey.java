package com.huawei.ascend.examples.a2a.gateway.model;

import java.util.Objects;

public record RouteCacheKey(
        String tenantId,
        String sourceAgentId,
        String targetAgentId,
        String a2aMethod) {

    public RouteCacheKey {
        tenantId = required(tenantId, "tenantId");
        sourceAgentId = required(sourceAgentId, "sourceAgentId");
        targetAgentId = required(targetAgentId, "targetAgentId");
        a2aMethod = required(a2aMethod, "a2aMethod");
    }

    public static RouteCacheKey from(RouteGrant grant) {
        return new RouteCacheKey(
                grant.tenantId(),
                grant.sourceAgentId(),
                grant.targetAgentId(),
                grant.allowedMethods().stream().findFirst().orElse("message/stream"));
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
