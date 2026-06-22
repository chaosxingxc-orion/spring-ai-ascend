package com.huawei.ascend.bus.forwarding.runtime.transport.a2a;

import java.util.Objects;

/**
 * Configuration for {@link A2aForwardingDeliveryPort} (Stage 15 PoC).
 *
 * <p>Plain Java record — injected exactly like {@code ForwardingRetryPolicy} /
 * {@code DispatchLeasePolicy} / {@code EpochClock}. Carries:
 *
 * <ul>
 *   <li>{@code streamTimeoutMillis} — how long {@code deliver} blocks on the A2A
 *       stream waiting for a terminal Task event before classifying
 *       {@code DELIVERY_TIMEOUT}. PoC default is short (2s) so the timeout
 *       scenario is testable without a long sleep; production tunes per-SLA.
 *   <li>{@code tenantHeaderName} — the HTTP header carrying the tenant id
 *       (Rule R-C.c, cross-tenant continuity). Matches agent-runtime's
 *       {@code A2aJsonRpcController} {@code @RequestHeader} so the same header
 *       name is written here and read on the receiving side. The header value
 *       comes from the outbox record's {@code tenantId}.
 * </ul>
 *
 * <p>Authority: Stage 15 PoC — A2A transport adapter
 * ({@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage14-review-and-stage15-plan.md}).
 */
public record A2aForwardingProperties(long streamTimeoutMillis, String tenantHeaderName) {

    public A2aForwardingProperties {
        if (streamTimeoutMillis <= 0L) {
            throw new IllegalArgumentException(
                    "streamTimeoutMillis must be > 0: " + streamTimeoutMillis);
        }
        Objects.requireNonNull(tenantHeaderName, "tenantHeaderName is required");
        if (tenantHeaderName.isBlank()) {
            throw new IllegalArgumentException("tenantHeaderName must not be blank");
        }
    }

    /** PoC default: 2s stream timeout, agent-runtime-compatible tenant header. */
    public static final A2aForwardingProperties DEFAULT =
            new A2aForwardingProperties(2_000L, "X-Tenant-Id");
}
