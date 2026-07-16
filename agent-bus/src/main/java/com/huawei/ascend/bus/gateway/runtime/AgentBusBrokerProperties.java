package com.huawei.ascend.bus.gateway.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent-bus two-form runtime connection config (arch-driven G5-B). Bound from
 * application properties under the {@code agent-bus.} prefix (e.g.
 * {@code agent-bus.nameserver}, {@code agent-bus.gateway-service-id}). Carries the
 * broker endpoints, the per-form service ids, the timing knobs the
 * {@code GatewayRuntimeService} / {@code EventBusRelayWorker} need, and the subscribe
 * tenant scope.
 *
 * <p>Optional timing fields get defaults in the compact constructor; {@code nameserver}
 * / {@code namespace} / {@code producer-group} are required and are validated downstream
 * by {@code BrokerClientProperties} / {@code DefaultMQProducer} construction (a missing
 * nameserver fails the context boot, fail-fast). {@code tenant} is the subscribe scope
 * for the relay consumers (single-tenant-per-deployment; multi-tenant relay subscribe
 * is a G5-E refinement — see the {@code deviations.md} FIXME).
 *
 * <p>Lives in {@code gateway.runtime} — the Spring-permitted wiring home (the
 * {@code GatewayRuntimeController} already proves this package is Spring-web-licensed).
 */
@ConfigurationProperties(prefix = "agent-bus")
public record AgentBusBrokerProperties(
        String nameserver,
        String namespace,
        String producerGroup,
        long pollWaitMillis,
        String gatewayServiceId,
        String eventBusServiceId,
        long acceptTimeoutMs,
        long responseTimeoutMs,
        long leaseDurationMs,
        String tenant,
        int relayTickLimit,
        long relayFixedDelayMs
) {
    public AgentBusBrokerProperties {
        if (pollWaitMillis <= 0) {
            pollWaitMillis = 3_000L;
        }
        if (gatewayServiceId == null || gatewayServiceId.isBlank()) {
            gatewayServiceId = "gateway";
        }
        if (eventBusServiceId == null || eventBusServiceId.isBlank()) {
            eventBusServiceId = "event-bus";
        }
        if (acceptTimeoutMs <= 0) {
            acceptTimeoutMs = 30_000L;
        }
        if (responseTimeoutMs <= 0) {
            responseTimeoutMs = 60_000L;
        }
        if (leaseDurationMs <= 0) {
            leaseDurationMs = 60_000L;
        }
        if (tenant == null || tenant.isBlank()) {
            tenant = "default";
        }
        if (relayTickLimit <= 0) {
            relayTickLimit = 100;
        }
        if (relayFixedDelayMs <= 0) {
            relayFixedDelayMs = 1_000L;
        }
    }
}
