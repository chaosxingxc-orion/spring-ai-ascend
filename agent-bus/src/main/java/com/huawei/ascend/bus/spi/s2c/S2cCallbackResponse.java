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
 * transitions to FAILED with reason
 * {@link com.huawei.ascend.runtime.resilience.spi.SuspendReason.AwaitClientCallback#S2C_RESPONSE_INVALID}.
 *
 * <p>Lives in {@code com.huawei.ascend.bus.spi.s2c} (moved from the old
 * runtime S2C package per the cross-constraint audit) so the SPI literally imports
 * only {@code java.*} + same-spi-package siblings.
 *
 * <p>Authority: ADR-0074; CLAUDE.md Rule 46.
 */
// scope: process-internal — transport envelope; tenant carried by the receiving Orchestrator context
public record S2cCallbackResponse(
        UUID callbackId,                 // MUST match request.callbackId
        Outcome outcome,                 // ok | error | timeout
        String clientTraceId,            // W3C 32-char lowercase hex; runtime correlates client execution
        Object responsePayload,          // opaque, capability-specific
        String errorCode,                // present only when outcome=error
        String errorMessage,             // present only when outcome=error
        Map<String, Object> responseAttributes
) {
    public S2cCallbackResponse {
        Objects.requireNonNull(callbackId, "callbackId is required");
        Objects.requireNonNull(outcome, "outcome is required");
        S2cCallbackEnvelope.requireLowerHex32(clientTraceId, "clientTraceId");
        if (outcome == Outcome.ERROR) {
            Objects.requireNonNull(errorCode, "errorCode is required when outcome=ERROR");
        }
        responseAttributes = responseAttributes == null ? Map.of() : Map.copyOf(responseAttributes);
    }

    public static S2cCallbackResponse ok(UUID callbackId, String clientTraceId, Object payload) {
        return new S2cCallbackResponse(callbackId, Outcome.OK, clientTraceId, payload, null, null, Map.of());
    }

    public static S2cCallbackResponse error(UUID callbackId, String clientTraceId, String code, String message) {
        return new S2cCallbackResponse(callbackId, Outcome.ERROR, clientTraceId, null, code, message, Map.of());
    }

    public static S2cCallbackResponse timeout(UUID callbackId, String clientTraceId) {
        return new S2cCallbackResponse(callbackId, Outcome.TIMEOUT, clientTraceId, null, null, null, Map.of());
    }

    /** Closed outcome set per s2c-callback.v1.yaml#outcome_values. */
    public enum Outcome { OK, ERROR, TIMEOUT }
}
