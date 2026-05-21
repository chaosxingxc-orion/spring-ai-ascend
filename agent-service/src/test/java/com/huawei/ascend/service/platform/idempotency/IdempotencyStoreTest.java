package com.huawei.ascend.service.platform.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
}
