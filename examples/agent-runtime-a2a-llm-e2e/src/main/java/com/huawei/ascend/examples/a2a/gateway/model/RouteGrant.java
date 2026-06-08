package com.huawei.ascend.examples.a2a.gateway.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record RouteGrant(
        String grantId,
        String tenantId,
        String sourceAgentId,
        String targetAgentId,
        RuntimeInstanceId targetRuntimeId,
        URI a2aEndpoint,
        Set<String> allowedMethods,
        long policyVersion,
        Instant issuedAt,
        Instant expiresAt,
        String signature) {

    public RouteGrant {
        grantId = required(grantId, "grantId");
        tenantId = required(tenantId, "tenantId");
        sourceAgentId = required(sourceAgentId, "sourceAgentId");
        targetAgentId = required(targetAgentId, "targetAgentId");
        targetRuntimeId = Objects.requireNonNull(targetRuntimeId, "targetRuntimeId");
        a2aEndpoint = Objects.requireNonNull(a2aEndpoint, "a2aEndpoint");
        allowedMethods = Set.copyOf(Objects.requireNonNull(allowedMethods, "allowedMethods"));
        if (allowedMethods.isEmpty()) {
            throw new IllegalArgumentException("allowedMethods must not be empty");
        }
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        signature = required(signature, "signature");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
