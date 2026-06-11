package com.huawei.ascend.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Authentication headers for the platform's A2A and service-facade ingress
 * edges, which share one scheme (ADR-0040): {@code Authorization: Bearer
 * <jwt>} whose {@code tenant_id} claim is cross-checked against an optional
 * {@code X-Tenant-Id} header — the ingress rejects a request that claims one
 * tenant in the header and authenticates as another.
 *
 * <p>The token is a {@link Supplier} (not a captured string) so callers can
 * plug rotating/short-lived credentials; it is re-evaluated on every call.
 */
public final class ClientAuth {

    private final Supplier<String> token;
    private final String tenantId;

    private ClientAuth(Supplier<String> token, String tenantId) {
        this.token = Objects.requireNonNull(token, "token");
        this.tenantId = tenantId;
    }

    /** Bearer token only; the server derives the tenant from the JWT claim. */
    public static ClientAuth jwtBearer(Supplier<String> token) {
        return new ClientAuth(token, null);
    }

    /**
     * Bearer token plus the explicit {@code X-Tenant-Id} header. The header
     * MUST match the JWT's {@code tenant_id} claim or the ingress answers 403.
     */
    public static ClientAuth jwtBearer(Supplier<String> token, String tenantId) {
        return new ClientAuth(token, tenantId);
    }

    /** The explicit tenant attribution; null when the JWT claim alone carries it. */
    String tenantId() {
        return tenantId;
    }

    /** Headers for one call; insertion order kept for readable wire captures. */
    Map<String, String> headers() {
        String value = Objects.requireNonNull(token.get(), "JWT token supplier returned null");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + value);
        if (tenantId != null && !tenantId.isBlank()) {
            headers.put("X-Tenant-Id", tenantId);
        }
        return headers;
    }
}
