package ascend.springai.bus.spi.s2c;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-to-Client (S2C) capability invocation request envelope.
 *
 * <p>Schema authority: {@code docs/contracts/s2c-callback.v1.yaml#request}.
 * The Phase 3a cross-rule audit matrix (see
 * {@code docs/logs/reviews/2026-05-16-engine-contract-structural-response.en.md} §5.2)
 * defines six mandatory fields that MUST appear on every S2C envelope at every
 * layer (envelope class, transport SPI, response validator, integration test,
 * audit log). The record below validates the six on construction.
 *
 * <p>Lives in {@code runtime.s2c.spi} (moved from {@code runtime.s2c} in
 * v2.0.0-rc3 per cross-constraint audit α-4 / β-2) so the SPI literally imports
 * only {@code java.*} + same-spi-package siblings, restoring exact agreement
 * with the ARCHITECTURE.md SPI-purity prose.
 *
 * <p>Authority: ADR-0074; CLAUDE.md Rule 46 (S2C Callback Envelope + Lifecycle Bound).
 */
// scope: process-internal — transport envelope; tenant resolved from callbackId via registry at the wrapping Run boundary (ADR-0074 §Consequences)
public record S2cCallbackEnvelope(
        UUID callbackId,            // primary correlation key
        UUID serverRunId,           // suspending Run id
        String capabilityRef,       // declared client capability id
        Object requestPayload,      // opaque, validated by capability-specific schema (W3)
        String traceId,             // W3C 32-char lowercase hex; MUST equal suspending Run.traceId
        UUID idempotencyKey,        // client may retry; runtime dedupes within window
        Instant deadline,           // absolute deadline; null means "use skill-capacity timeout_ms"
        Map<String, Object> requestAttributes  // optional capability-specific extras
) {
    public S2cCallbackEnvelope {
        Objects.requireNonNull(callbackId, "callbackId is required");
        Objects.requireNonNull(serverRunId, "serverRunId is required");
        Objects.requireNonNull(capabilityRef, "capabilityRef is required");
        if (capabilityRef.isBlank()) {
            throw new IllegalArgumentException("capabilityRef must not be blank");
        }
        Objects.requireNonNull(requestPayload, "requestPayload is required");
        requireLowerHex32(traceId, "traceId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        // deadline + requestAttributes are optional
        requestAttributes = requestAttributes == null ? Map.of() : Map.copyOf(requestAttributes);
    }

    /**
     * Enforce the W3C trace-id schema literally: exactly 32 lowercase hex
     * chars (0-9, a-f). Added in v2.0.0-rc3 per cross-constraint audit α-5 /
     * P1-5 — prior code validated only {@code length() != 32} so the contract
     * text "lowercase hex" was unenforced.
     */
    static void requireLowerHex32(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.length() != 32) {
            throw new IllegalArgumentException(fieldName + " must be exactly 32 lowercase hex chars (W3C)");
        }
        for (int i = 0; i < 32; i++) {
            char c = value.charAt(i);
            boolean isLowerHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!isLowerHex) {
                throw new IllegalArgumentException(fieldName + " must be exactly 32 lowercase hex chars (W3C); offending char at index " + i + ": '" + c + "'");
            }
        }
    }
}
