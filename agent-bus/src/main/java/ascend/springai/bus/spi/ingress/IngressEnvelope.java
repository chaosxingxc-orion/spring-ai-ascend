package ascend.springai.bus.spi.ingress;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Client-to-server ingress request envelope.
 *
 * <p>Schema authority: {@code docs/contracts/ingress-envelope.v1.yaml#request}.
 * Six mandatory fields plus optional deadline + attributes. The
 * {@code tenantId} field satisfies Rule R-C sub-clause .c (Contract Spine
 * Completeness) — every persistent record carries a non-null tenant scope
 * validated in the compact constructor.
 *
 * <p>Authority: ADR-0089; CLAUDE.md Rule R-I sub-clause .b.
 */
// scope: process-internal — ingress envelope; tenant scope validated below
public record IngressEnvelope(
        UUID requestId,                       // primary correlation key
        String tenantId,                      // Rule R-C.c contract spine
        UUID idempotencyKey,                  // client may retry; bus dedupes within window
        IngressRequestType requestType,       // RUN_CREATE | RUN_GET | RUN_CANCEL | RUN_RESUME
        Object payload,                       // opaque; validated by request_type-specific subschema
        String traceId,                       // W3C 32-char lowercase hex (ADR-0061)
        Long deadlineMillisEpoch,             // optional absolute deadline; null = no deadline override
        Map<String, Object> requestAttributes // optional capability-specific extras
) {
    public IngressEnvelope {
        Objects.requireNonNull(requestId, "requestId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(requestType, "requestType is required");
        Objects.requireNonNull(payload, "payload is required");
        requireLowerHex32(traceId, "traceId");
        requestAttributes = requestAttributes == null ? Map.of() : Map.copyOf(requestAttributes);
    }

    /**
     * Enforce the W3C trace-id schema literally: exactly 32 lowercase hex
     * chars (0-9, a-f). Mirrors the validator used by
     * {@code S2cCallbackEnvelope.requireLowerHex32} so the two cross-plane
     * surfaces apply the same trace-id discipline.
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

    /** Closed enum mirroring ingress-envelope.v1.yaml#request.required_fields.request_type. */
    public enum IngressRequestType {
        RUN_CREATE, RUN_GET, RUN_CANCEL, RUN_RESUME
    }
}
