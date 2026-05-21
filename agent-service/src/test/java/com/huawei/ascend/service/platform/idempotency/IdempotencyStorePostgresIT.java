package com.huawei.ascend.service.platform.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer-2 integration test for {@link JdbcIdempotencyStore} (ADR-0057). Brings up
 * a real Postgres via Testcontainers, runs Flyway migrations (V1__init.sql plus
 * the new V2__idempotency_dedup.sql), and exercises the claim / replay / body-
 * drift paths against the live database.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E12, #E13, #E14.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IdempotencyStorePostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("springaiascend")
            .withUsername("springaiascend")
            .withPassword("springaiascend");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Keep dev-posture defaults; JdbcIdempotencyStore wires automatically when the
        // DataSource is present.
        registry.add("app.posture", () -> "dev");
        registry.add("app.idempotency.ttl", () -> "PT1H");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IdempotencyStore store;

    @BeforeEach
    void clearTable() {
        jdbc.update("DELETE FROM idempotency_dedup");
    }

    @Test
    void wired_bean_is_jdbc_implementation() {
        assertThat(store).isInstanceOf(JdbcIdempotencyStore.class);
    }

    @Test
    void first_claim_inserts_row_and_returns_empty() {
        UUID tenant = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        Optional<IdempotencyStore.IdempotencyRecord> result = store.claimOrFind(tenant, key, "hash-1");

        assertThat(result).isEmpty();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_dedup WHERE tenant_id = ? AND idempotency_key = ?",
                Integer.class, tenant, key);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void duplicate_claim_returns_existing_with_same_hash() {
        UUID tenant = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        store.claimOrFind(tenant, key, "hash-1");

        Optional<IdempotencyStore.IdempotencyRecord> dup = store.claimOrFind(tenant, key, "hash-1");
        assertThat(dup).isPresent();
        assertThat(dup.get().requestHash()).isEqualTo("hash-1");
        assertThat(dup.get().status()).isEqualTo(IdempotencyStore.Status.CLAIMED);
    }

    @Test
    void body_drift_returns_existing_with_original_hash() {
        UUID tenant = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        store.claimOrFind(tenant, key, "hash-original");

        Optional<IdempotencyStore.IdempotencyRecord> drift = store.claimOrFind(tenant, key, "hash-different");
        assertThat(drift).isPresent();
        assertThat(drift.get().requestHash()).isEqualTo("hash-original");
    }

    @Test
    void same_key_in_different_tenants_does_not_collide() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID sharedKey = UUID.randomUUID();

        assertThat(store.claimOrFind(tenantA, sharedKey, "h")).isEmpty();
        assertThat(store.claimOrFind(tenantB, sharedKey, "h")).isEmpty();

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_dedup WHERE idempotency_key = ?",
                Integer.class, sharedKey);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void schema_check_constraint_rejects_illegal_status() {
        // Direct INSERT bypassing the store: the storage-layer CHECK constraint
        // (enforcer E13 schema kind) must reject illegal status values.
        UUID tenant = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        try {
            jdbc.update("""
                            INSERT INTO idempotency_dedup
                                (tenant_id, idempotency_key, request_hash, status, expires_at)
                            VALUES (?, ?, ?, 'BOGUS', now() + INTERVAL '1 hour')""",
                    tenant, key, "h");
            assertThat(false).as("Expected CHECK constraint to reject status='BOGUS'").isTrue();
        } catch (org.springframework.dao.DataIntegrityViolationException expected) {
            // ok
        }
    }

    @Test
    void expires_at_uses_configured_ttl() {
        UUID tenant = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        store.claimOrFind(tenant, key, "h");

        Optional<IdempotencyStore.IdempotencyRecord> rec = store.claimOrFind(tenant, key, "h");
        assertThat(rec).isPresent();
        Duration window = Duration.between(rec.get().createdAt(), rec.get().expiresAt());
        assertThat(window.toHours()).isEqualTo(1L);
    }
}
