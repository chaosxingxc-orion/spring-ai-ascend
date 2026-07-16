package com.huawei.ascend.bus.forwarding.spi.broker;

import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;

import java.util.Objects;

/**
 * Broker-agnostic inbound message returned by {@link BrokerForwardingConsumerPort#poll}
 * (Stage 26, T4 hybrid receiver).
 *
 * <p>Mirrors the routing metadata of {@link BrokerOutboundMessage} without exposing
 * ANY broker product concept (topic / partition / offset) — those are adapter-internal
 * (decision §6.2 ① spirit). The receiver uses {@code tenantId} / {@code messageId} /
 * {@code consumerServiceId} as the inbox dedup key {@code (tenantId, messageId,
 * consumerServiceId)}; {@code payloadRef} (when present) is the HD4 data reference
 * to fetch the payload body externally.
 *
 * <p>{@code consumerServiceId} is the broker consumer-group materialised into the
 * in-flight message at poll time (the polling consumer owns it until commit / reject);
 * {@link BrokerForwardingConsumerPort#commit} / {@link BrokerForwardingConsumerPort#reject}
 * read it back so the adapter advances / replays the right consumer-group offset
 * (model B ack-after-consume, at-least-once).
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/
 * agent-bus-forwarding-runtime-transport-decision.md} (Stage 25 §10 guardrails,
 * Stage 26 broker SPI scaffold).
 */
// scope: forwarding transport.broker — broker-agnostic inbound message; no offset/topic/partition leaked
public record BrokerInboundMessage(
        String tenantId,
        String messageId,
        String sourceServiceId,
        String targetServiceId,
        String consumerServiceId,
        String payloadRef,
        // FEAT-013 cross-hop correlation key (mirrored from headers at poll). Nullable (control-only /
        // JDBC back-compat); the gateway (S2) matches responses by this field (L2 feat-013 §4.2).
        String correlationId,
        // FEAT-013/014 event-type discriminator (mirrored from headers at poll). Nullable; the gateway
        // (S2) classifies responses by this field (L2 §4.2), avoiding descriptor-decoding coupling.
        AgentBusEventType eventType
) {
    public BrokerInboundMessage {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(messageId, "messageId");
        requireNonBlank(sourceServiceId, "sourceServiceId");
        requireNonBlank(targetServiceId, "targetServiceId");
        requireNonBlank(consumerServiceId, "consumerServiceId");
        if (payloadRef != null && payloadRef.isBlank()) {
            throw new IllegalArgumentException("payloadRef must be null or non-blank");
        }
        // correlationId conditional: null (control-only / JDBC back-compat) or non-blank.
        if (correlationId != null && correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must be null or non-blank");
        }
        // eventType: nullable (control-only / JDBC back-compat); no further validation (enum).
    }

    /** Whether this message carries a payload reference (data-bearing). */
    public boolean carriesPayloadRef() {
        return payloadRef != null;
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
