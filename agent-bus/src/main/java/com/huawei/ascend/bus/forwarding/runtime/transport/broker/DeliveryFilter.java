package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import java.util.Map;
import java.util.Objects;

/**
 * Broker-agnostic named-property criteria a receiver subscribes with (decision packet
 * agent-bus-broker-filtering-spi-completion §3 / D3). The SPI layer sees only structured
 * name=value criteria; the RocketMQ adapter translates these to {@code MessageSelector.bySql(...)},
 * other brokers to their equivalent (header binding / filter policy / subject pattern), and
 * brokers that cannot filter broker-side (Kafka / Redis Streams) degrade to client-side
 * filtering (D7) — the {@code requiredProperties} contract is identical either way.
 *
 * <p>{@link #forRuntime(String, String)} stamps the tenant + target-serviceId a runtime
 * wants to receive: the producer already sets {@code targetServiceId} as a userProperty
 * ({@code RocketMqBrokerForwardingRelay}), so this is the matching half of a shared-property
 * contract (D2 / D12 — no producer filter parameter; targeting is expressed via the envelope
 * property, not a producer-side filter argument).
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/
 * agent-bus-broker-filtering-spi-completion-decision.md} §3 (D3 / D4 / D5 / D8).
 */
// scope: forwarding transport.broker — broker-agnostic delivery filter criteria (receiver SPI surface)
public record DeliveryFilter(Map<String, String> requiredProperties) {

    public DeliveryFilter {
        requiredProperties = Map.copyOf(requiredProperties); // null-map -> NPE; immutable view
    }

    /**
     * Stamp the tenant + target-serviceId a runtime wants to receive — the canonical
     * per-runtime filter (D2 / D10: a {@code runtime-${serviceId}} consumer-group receives
     * only messages targeted at it, within its tenant).
     */
    public static DeliveryFilter forRuntime(String tenantId, String myServiceId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(myServiceId, "myServiceId is required");
        if (tenantId.isBlank() || myServiceId.isBlank()) {
            throw new IllegalArgumentException("tenantId and myServiceId must not be blank");
        }
        return new DeliveryFilter(Map.of("tenantId", tenantId, "targetServiceId", myServiceId));
    }
}
