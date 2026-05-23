package com.huawei.ascend.service.platform.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link IdempotencyStore} contract, exercised against the
 * {@link InMemoryIdempotencyStore} reference implementation (ADR-0057). The same
 * contract is exercised against {@link JdbcIdempotencyStore} by
 * {@code IdempotencyStorePostgresIT}.
 *
 * <p>Related enforcers in enforcers.yaml: E12, E14 (the primary IT-level
 * enforcers for idempotency durability live in IdempotencyDurabilityIT and
 * IdempotencyStorePostgresIT respectively). This unit-test is documentation-
 * level coverage for the SPI shape; no `#` form so Rule 28k stays scoped to
 * primary-citation checks.
 */
class IdempotencyStoreTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private Clock clock;
    private InMemoryIdempotencyStore store;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);
        store = new InMemoryIdempotencyStore(clock, Duration.ofHours(24));
    }

    @Test
    void first_claim_returns_empty() {
        Optional<IdempotencyStore.IdempotencyRecord> result =
                store.claimOrFind(TENANT, UUID.randomUUID(), "hash-1");
        assertThat(result).isEmpty();
    }

    @Test
    void duplicate_claim_returns_existing_record_with_same_hash() {
        UUID key = UUID.randomUUID();
        store.claimOrFind(TENANT, key, "hash-1");
        Optional<IdempotencyStore.IdempotencyRecord> result =
                store.claimOrFind(TENANT, key, "hash-1");
        assertThat(result).isPresent();
        assertThat(result.get().requestHash()).isEqualTo("hash-1");
        assertThat(result.get().status()).isEqualTo(IdempotencyStore.Status.CLAIMED);
    }

    @Test
    void body_drift_detected_when_same_key_used_with_different_hash() {
        UUID key = UUID.randomUUID();
        store.claimOrFind(TENANT, key, "hash-original");
        Optional<IdempotencyStore.IdempotencyRecord> result =
                store.claimOrFind(TENANT, key, "hash-different");
        assertThat(result).isPresent();
        assertThat(result.get().requestHash()).isEqualTo("hash-original");
    }

    @Test
    void same_key_in_different_tenants_does_not_collide() {
        UUID sharedKey = UUID.randomUUID();
        assertThat(store.claimOrFind(TENANT, sharedKey, "h")).isEmpty();
        assertThat(store.claimOrFind(OTHER_TENANT, sharedKey, "h")).isEmpty();
    }

    @Test
    void claim_record_has_expected_expires_at() {
        UUID key = UUID.randomUUID();
        store.claimOrFind(TENANT, key, "h");
        Optional<IdempotencyStore.IdempotencyRecord> rec = store.claimOrFind(TENANT, key, "h");
        assertThat(rec).isPresent();
        assertThat(rec.get().expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
    }

    @Test
    void expired_claim_is_replaced_on_retry_per_adr_0057_ttl_recovery() {
        // ADR-0057 §2: L1 stops at CLAIMED and "falls back to TTL expiry for
        // retried failures". A retry whose prior claim's expires_at has
        // elapsed MUST be accepted as a fresh claim, not rejected as a
        // duplicate. Prior to the fix the prior record was returned
        // regardless of expiry, locking the key forever.
        MutableClock movingClock = new MutableClock(Instant.parse("2026-05-14T12:00:00Z"));
        InMemoryIdempotencyStore ttlStore = new InMemoryIdempotencyStore(movingClock, Duration.ofMinutes(5));
        UUID key = UUID.randomUUID();

        assertThat(ttlStore.claimOrFind(TENANT, key, "hash-original")).isEmpty();
        assertThat(ttlStore.claimOrFind(TENANT, key, "hash-original"))
                .as("pre-TTL retry must still see the original claim")
                .isPresent();

        movingClock.advance(Duration.ofMinutes(6));

        assertThat(ttlStore.claimOrFind(TENANT, key, "hash-new"))
                .as("post-TTL retry must be accepted as a fresh claim")
                .isEmpty();

        Optional<IdempotencyStore.IdempotencyRecord> peek =
                ttlStore.claimOrFind(TENANT, key, "hash-new");
        assertThat(peek).isPresent();
        assertThat(peek.get().requestHash())
                .as("the renewed claim carries the new hash, not the expired one")
                .isEqualTo("hash-new");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        void advance(Duration d) {
            this.instant = this.instant.plus(d);
        }
    }
}
