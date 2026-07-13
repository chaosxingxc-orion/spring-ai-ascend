package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;

import java.util.Objects;

/**
 * Broker-agnostic message headers carried alongside a {@link BrokerOutboundMessage}
 * body (Stage 26, T4 hybrid).
 *
 * <p>These headers are the broker-side vehicle for the routing metadata a receiver
 * needs to dedup / route / fetch the payload — they are NOT the payload body
 * (decision §6.2 ②: payload body / token stream / Task state never enter the
 * broker message). {@code payloadRef} rides as a header (conditionally required,
 * mirroring {@code ForwardingEnvelope.PayloadPolicy}: present for data-bearing
 * messages, absent for control-only).
 *
 * <p>Broker product concepts (topic / partition / offset / consumer-group) NEVER
 * appear here — those are adapter-internal (decision §6.2 ① spirit). {@code tenantId}
 * is mandatory and is the L2 header-tenant-check defense (a receiver polls with its
 * own tenant and rejects any message whose header tenantId differs — decision §6.2 ⑤).
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/
 * agent-bus-forwarding-runtime-transport-decision.md} (Stage 25 §10 guardrails,
 * Stage 26 broker SPI scaffold).
 */
// scope: forwarding transport.broker — routing metadata headers; never a payload body
public record BrokerMessageHeaders(
        String tenantId,
        String messageId,
        String sourceServiceId,
        String targetServiceId,
        String payloadRef,
        // FEAT-013 cross-hop correlation key (mirrored from the outbox record at produce). Nullable:
        // control-only messages or JDBC-back-compat records carry null; the gateway matches responses by it.
        String correlationId,
        // FEAT-013/014 event-type discriminator (mirrored from the record at produce). Nullable; the
        // gateway (S2) classifies responses by this field (L2 §4.2), avoiding descriptor-decoding coupling.
        AgentBusEventType eventType
) {
    public BrokerMessageHeaders {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(messageId, "messageId");
        requireNonBlank(sourceServiceId, "sourceServiceId");
        requireNonBlank(targetServiceId, "targetServiceId");
        // payloadRef conditional: null (control-only) or non-blank (data-bearing)
        if (payloadRef != null && payloadRef.isBlank()) {
            throw new IllegalArgumentException("payloadRef must be null or non-blank");
        }
        // correlationId conditional: null (control-only / JDBC back-compat) or non-blank.
        if (correlationId != null && correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must be null or non-blank");
        }
        // eventType: nullable (control-only / JDBC back-compat); no further validation (enum).
    }

    /** Whether this header set carries a payload reference (data-bearing message). */
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
