package com.huawei.ascend.bus.spi.s2c;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-to-Client (S2C) capability invocation response envelope.
 *
 * <p>Schema authority: {@code docs/contracts/s2c-callback.v1.yaml#response}.
 *
 * <p>The response {@code callbackId} MUST match the originating request's
 * {@code callbackId}. Mismatch raises a validation error and the Run
 * transitions to FAILED with reason {@code S2C_RESPONSE_INVALID} (the
 * SuspendReason taxonomy is a design target — see the contract YAML).
 *
 * <p>Lives in {@code com.huawei.ascend.bus.spi.s2c} so the SPI literally
 * imports only {@code java.*} + same-spi-package siblings.
 *
 * <p>Authority: ADR-0074.
 */
// scope: process-internal — transport envelope; tenant carried by the receiving Orchestrator context
public record S2cCallbackResponse(
        UUID callbackId,                 // MUST match request.callbackId
        Outcome outcome,                 // ok | error | timeout
        String clientTraceId,            // W3C 32-char lowercase hex; null for TIMEOUT (no client execution)
        Object responsePayload,          // opaque, capability-specific
        String errorCode,                // present only when outcome=error
        String errorMessage,             // present only when outcome=error
        Map<String, Object> responseAttributes
) {
    public S2cCallbackResponse {
        Objects.requireNonNull(callbackId, "callbackId is required");
        Objects.requireNonNull(outcome, "outcome is required");
        if (outcome == Outcome.TIMEOUT) {
            // TIMEOUT means the client never responded — there is no client
            // execution to correlate, so forcing a fabricated trace id would put
            // lies on the wire. A trace id MAY still be present if partial
            // correlation exists.
            if (clientTraceId != null) {
                S2cCallbackEnvelope.requireLowerHex32(clientTraceId, "clientTraceId");
            }
        } else {
            S2cCallbackEnvelope.requireLowerHex32(clientTraceId, "clientTraceId");
        }
        if (outcome == Outcome.ERROR) {
            Objects.requireNonNull(errorCode, "errorCode is required when outcome=ERROR");
        } else {
            // OK/TIMEOUT with error fields is a contradictory envelope.
            if (errorCode != null || errorMessage != null) {
                throw new IllegalArgumentException(
                        "errorCode/errorMessage are only valid when outcome=ERROR, got outcome=" + outcome);
            }
        }
        responseAttributes = responseAttributes == null ? Map.of() : Map.copyOf(responseAttributes);
    }

    public static S2cCallbackResponse ok(UUID callbackId, String clientTraceId, Object payload) {
        return new S2cCallbackResponse(callbackId, Outcome.OK, clientTraceId, payload, null, null, Map.of());
    }

    public static S2cCallbackResponse error(UUID callbackId, String clientTraceId, String code, String message) {
        return new S2cCallbackResponse(callbackId, Outcome.ERROR, clientTraceId, null, code, message, Map.of());
    }

    /** Synthetic response the runtime builds when the client never answered — no trace to correlate. */
    public static S2cCallbackResponse timeout(UUID callbackId) {
        return new S2cCallbackResponse(callbackId, Outcome.TIMEOUT, null, null, null, null, Map.of());
    }

    /** Closed outcome set per s2c-callback.v1.yaml#outcome_values. */
    public enum Outcome { OK, ERROR, TIMEOUT }
}
