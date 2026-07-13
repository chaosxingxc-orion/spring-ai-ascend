package com.huawei.ascend.bus.forwarding.spi;

import java.util.Objects;

/**
 * Runtime-to-runtime forwarding envelope for the C3 outbox / inbox substrate.
 *
 * <p>Mirrors the Forwarding Envelope Required Fields of
 * {@code ICD-Agent-Bus-Forwarding} (HD4): tenantId, traceId, correlationId,
 * idempotencyKey, routeHandle, capability, deadline. {@code payloadRef} is
 * conditionally required (MI5-003 option B): mandatory when
 * {@link PayloadPolicy#DATA_BEARING}, optional for
 * {@link PayloadPolicy#CONTROL_ONLY}.
 *
 * <p>Forbidden-payload invariant (HD4): this envelope NEVER carries a payload
 * body, a token stream, or Task execution state. There is no such field, by
 * design — large payloads take the {@code payloadRef} data reference path. The
 * compact constructor additionally enforces tenant continuity: the envelope
 * {@code tenantId} must equal {@link ForwardingRouteHandle#tenantScope()}, else
 * {@link ForwardingFailureCode#TENANT_MISMATCH}.
 *
 * <p>FEAT-013+014 additive extension: {@code eventType} discriminates the
 * invocation / A2A-call event family (see {@link AgentBusEventType});
 * {@code sourceServiceId} / {@code targetServiceId} lift the source / target
 * route references onto the envelope so it is self-describing — they remain
 * mirrored on {@link ForwardingOutboxRecord}, with the envelope authoritative
 * (feat-013 §2.3.1). The compact constructor validates all three as non-null
 * and non-blank.
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding} (HD4);
 * {@code ICD-Agent-Bus-Forwarding-Runtime};
 * {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md §5/§6}.
 */
// scope: forwarding substrate — control + payloadRef only; never a payload body
public record ForwardingEnvelope(
        ForwardingMessageId messageId,
        AgentBusEventType eventType,
        String tenantId,
        String traceId,
        String correlationId,
        String idempotencyKey,
        ForwardingRouteHandle routeHandle,
        String capability,
        String sourceServiceId,
        String targetServiceId,
        long deadlineMillisEpoch,
        PayloadPolicy payloadPolicy,
        String payloadRef
) {
    public ForwardingEnvelope {
        Objects.requireNonNull(messageId, "messageId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(traceId, "traceId is required");
        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        Objects.requireNonNull(correlationId, "correlationId is required");
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        Objects.requireNonNull(routeHandle, "routeHandle is required");
        Objects.requireNonNull(capability, "capability is required");
        if (capability.isBlank()) {
            throw new IllegalArgumentException("capability must not be blank");
        }
        Objects.requireNonNull(sourceServiceId, "sourceServiceId is required");
        if (sourceServiceId.isBlank()) {
            throw new IllegalArgumentException("sourceServiceId must not be blank");
        }
        Objects.requireNonNull(targetServiceId, "targetServiceId is required");
        if (targetServiceId.isBlank()) {
            throw new IllegalArgumentException("targetServiceId must not be blank");
        }
        Objects.requireNonNull(payloadPolicy, "payloadPolicy is required");
        // tenant isolation: envelope tenant must equal the route's tenant scope (R-C.c)
        if (!tenantId.equals(routeHandle.tenantScope())) {
            throw new IllegalArgumentException(
                    "tenant_mismatch: envelope tenantId '" + tenantId
                    + "' must equal routeHandle tenantScope '" + routeHandle.tenantScope() + "'");
        }
        // payloadRef conditional required (MI5-003 option B)
        if (payloadPolicy == PayloadPolicy.DATA_BEARING) {
            Objects.requireNonNull(payloadRef,
                    "payloadRef is required for DATA_BEARING message");
            if (payloadRef.isBlank()) {
                throw new IllegalArgumentException(
                        "payloadRef must not be blank for DATA_BEARING message");
            }
        }
        if (payloadRef != null && payloadRef.isBlank()) {
            throw new IllegalArgumentException("payloadRef must be null or non-blank");
        }
    }

    /** Whether this envelope carries a payload reference (data-bearing message). */
    public boolean carriesPayloadRef() {
        return payloadRef != null;
    }

    /**
     * Payload presence policy — encodes the MI5-003 option B conditional
     * requirement for {@code payloadRef}.
     */
    public enum PayloadPolicy {
        /** Pure control message; payloadRef optional (typically absent). */
        CONTROL_ONLY,
        /** Carries external data / a large payload; payloadRef mandatory. */
        DATA_BEARING
    }
}
