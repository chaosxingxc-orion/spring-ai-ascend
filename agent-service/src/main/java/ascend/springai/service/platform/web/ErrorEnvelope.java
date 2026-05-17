package ascend.springai.service.platform.web;

import java.util.List;

/**
 * Stable error response shape for the W1 HTTP API (ADR-0056, ADR-0057, plan §6.4).
 *
 * <p>Every 4xx/5xx response MUST serialise to {@code {"error": {"code": "...",
 * "message": "...", "details": []}}}. No top-level field other than {@code error};
 * never a 200 with an embedded failure object.
 *
 * <p>{@code code} is a stable {@code snake_case} identifier (see plan §6.3 matrix
 * and {@code RunHttpContractIT}). {@code details} is reserved for field-level
 * validation breakdowns; empty list at L1.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E8.
 */
public record ErrorEnvelope(Error error) {

    public record Error(String code, String message, List<String> details) {
        public Error {
            details = details == null ? List.of() : List.copyOf(details);
        }
    }

    public static ErrorEnvelope of(String code, String message) {
        return new ErrorEnvelope(new Error(code, message, List.of()));
    }
}
