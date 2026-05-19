package ascend.springai.bus.spi.ingress;

import java.util.Objects;
import java.util.UUID;

/**
 * Bus acknowledgement envelope returned to the client by
 * {@link IngressGateway#routeClientRequest(IngressEnvelope)}.
 *
 * <p>Schema authority: {@code docs/contracts/ingress-envelope.v1.yaml#response}.
 * Long-running work returns {@link IngressStatus#ACCEPTED} with a
 * {@code cursor} populated per Rule R-F (Cursor Flow). Refused traffic
 * returns {@link IngressStatus#REJECTED} carrying a {@code rejectionReason}.
 * Bus-level deferrals (e.g. backpressure) return {@link IngressStatus#DEFERRED}.
 *
 * <p>Authority: ADR-0089; CLAUDE.md Rule R-I sub-clause .b.
 */
public record IngressResponse(
        UUID requestId,            // mirrors IngressEnvelope.requestId
        IngressStatus status,
        String cursor,             // present iff status == ACCEPTED && IngressEnvelope.requestType == RUN_CREATE
        String rejectionReason     // present iff status == REJECTED
) {
    public IngressResponse {
        Objects.requireNonNull(requestId, "requestId is required");
        Objects.requireNonNull(status, "status is required");
        if (status == IngressStatus.REJECTED) {
            Objects.requireNonNull(rejectionReason, "rejectionReason is required when status=REJECTED");
            if (rejectionReason.isBlank()) {
                throw new IllegalArgumentException("rejectionReason must not be blank when status=REJECTED");
            }
        }
    }

    public static IngressResponse accepted(UUID requestId, String cursor) {
        return new IngressResponse(requestId, IngressStatus.ACCEPTED, cursor, null);
    }

    public static IngressResponse rejected(UUID requestId, String reason) {
        return new IngressResponse(requestId, IngressStatus.REJECTED, null, reason);
    }

    public static IngressResponse deferred(UUID requestId) {
        return new IngressResponse(requestId, IngressStatus.DEFERRED, null, null);
    }

    /** Closed enum mirroring ingress-envelope.v1.yaml#response.required_fields.status. */
    public enum IngressStatus {
        ACCEPTED, REJECTED, DEFERRED
    }
}
