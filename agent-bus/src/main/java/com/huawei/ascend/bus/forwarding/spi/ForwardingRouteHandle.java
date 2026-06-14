package com.huawei.ascend.bus.forwarding.spi;

import java.util.Objects;

/**
 * Opaque route handle returned by Stage 3 discovery, carried on the forwarding
 * envelope so the dispatcher targets a route without ever touching a physical
 * endpoint.
 *
 * <p>{@code value} is the opaque handle — it encapsulates endpoint / topic /
 * serviceId / routeKey per {@code ICD-Agent-Registry-Discovery}; forwarding
 * never exposes or bypasses the physical endpoint (HD4).
 *
 * <p>{@code tenantScope} binds the route to a single tenant. The forwarding
 * envelope's {@code tenantId} must equal it (tenant isolation, Rule R-C.c); a
 * mismatch is rejected as {@link ForwardingFailureCode#TENANT_MISMATCH} at
 * envelope construction.
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding} (HD4 Route Handle);
 * {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §6}.
 */
// scope: forwarding substrate — opaque discovery handle + tenant scope
public record ForwardingRouteHandle(String value, String tenantScope) {
    public ForwardingRouteHandle {
        Objects.requireNonNull(value, "value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        Objects.requireNonNull(tenantScope, "tenantScope is required");
        if (tenantScope.isBlank()) {
            throw new IllegalArgumentException("tenantScope must not be blank");
        }
    }
}
