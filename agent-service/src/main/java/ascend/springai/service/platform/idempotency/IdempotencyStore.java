package ascend.springai.service.platform.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * L1 tenant-scoped idempotency dedup contract (ADR-0057).
 *
 * <p>One {@link #claimOrFind(UUID, UUID, String)} call per mutating HTTP request:
 * the first request with a given {@code (tenantId, key)} pair claims the row;
 * concurrent or replayed requests see {@link Optional#isPresent() the existing
 * record} and the caller turns it into a 409 response.
 *
 * <p>L1 stops at the {@code CLAIMED} status. W2 will add {@code COMPLETED}/
 * {@code FAILED} transitions plus response replay. Until then, retried failed
 * runs recover via {@code expires_at} TTL.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E12, #E13, #E14.
 */
public interface IdempotencyStore {

    /**
     * Attempt to claim the {@code (tenantId, key)} composite for the given
     * {@code requestHash}. Returns {@code Optional.empty()} when the claim was
     * newly inserted (caller proceeds); {@code Optional.of(existing)} when a row
     * already exists for the composite (caller compares {@code requestHash} and
     * returns 409 with the appropriate code).
     */
    Optional<IdempotencyRecord> claimOrFind(UUID tenantId, UUID key, String requestHash);

    /** Status values mirror the schema CHECK constraint. */
    enum Status {
        CLAIMED,
        COMPLETED,
        FAILED
    }

    /** Immutable snapshot of a dedup row. */
    record IdempotencyRecord(
            UUID tenantId,
            UUID idempotencyKey,
            String requestHash,
            Status status,
            Integer responseStatus,
            String responseBodyRef,
            Instant createdAt,
            Instant completedAt,
            Instant expiresAt
    ) {
    }
}
