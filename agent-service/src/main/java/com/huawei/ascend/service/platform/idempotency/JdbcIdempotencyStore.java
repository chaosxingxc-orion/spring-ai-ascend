package com.huawei.ascend.service.platform.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed {@link IdempotencyStore} (ADR-0057 §3).
 *
 * <p>Claim semantics: {@code INSERT … ON CONFLICT (tenant_id, idempotency_key)
 * DO NOTHING}. The schema {@code PRIMARY KEY} (enforcer E13) makes the conflict
 * race-free at the database layer.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E12, #E13, #E14.
 */
public class JdbcIdempotencyStore implements IdempotencyStore {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcIdempotencyStore.class);

    /**
     * Upsert that claims the row when (a) no prior row exists OR (b) the prior
     * row's TTL has elapsed. ADR-0057 §2 mandates TTL-based recovery for L1
     * because COMPLETED/FAILED status transitions are deferred to W2; the
     * narrow WHERE clause on DO UPDATE preserves the W1 "first claim wins
     * until expires_at" semantic atomically at the storage layer.
     */
    private static final String INSERT_SQL = """
            INSERT INTO idempotency_dedup
                (tenant_id, idempotency_key, request_hash, status, created_at, expires_at)
            VALUES
                (:tenantId, :key, :requestHash, 'CLAIMED', :createdAt, :expiresAt)
            ON CONFLICT (tenant_id, idempotency_key) DO UPDATE
                SET request_hash = EXCLUDED.request_hash,
                    status = 'CLAIMED',
                    response_status = NULL,
                    response_body_ref = NULL,
                    created_at = EXCLUDED.created_at,
                    completed_at = NULL,
                    expires_at = EXCLUDED.expires_at
                WHERE idempotency_dedup.expires_at <= EXCLUDED.created_at
            """;

    private static final String SELECT_SQL = """
            SELECT tenant_id, idempotency_key, request_hash, status,
                   response_status, response_body_ref, created_at, completed_at, expires_at
            FROM idempotency_dedup
            WHERE tenant_id = :tenantId AND idempotency_key = :key
            """;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration ttl;

    public JdbcIdempotencyStore(JdbcClient jdbc, Clock clock, Duration ttl) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public Optional<IdempotencyRecord> claimOrFind(UUID tenantId, UUID key, String requestHash) {
        Instant now = clock.instant();
        Instant expires = now.plus(ttl);

        int inserted;
        try {
            inserted = jdbc.sql(INSERT_SQL)
                    .param("tenantId", tenantId)
                    .param("key", key)
                    .param("requestHash", requestHash)
                    .param("createdAt", Timestamp.from(now))
                    .param("expiresAt", Timestamp.from(expires))
                    .update();
        } catch (DuplicateKeyException dke) {
            // Some JDBC drivers translate the PRIMARY KEY conflict into a
            // DuplicateKeyException despite the ON CONFLICT clause. Treat as
            // "row exists" and fall through to SELECT.
            inserted = 0;
        }

        if (inserted > 0) {
            return Optional.empty();
        }

        return jdbc.sql(SELECT_SQL)
                .param("tenantId", tenantId)
                .param("key", key)
                .query((rs, n) -> new IdempotencyRecord(
                        (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("idempotency_key"),
                        rs.getString("request_hash"),
                        Status.valueOf(rs.getString("status")),
                        rs.getObject("response_status", Integer.class),
                        rs.getString("response_body_ref"),
                        rs.getTimestamp("created_at").toInstant(),
                        Optional.ofNullable(rs.getTimestamp("completed_at"))
                                .map(Timestamp::toInstant).orElse(null),
                        rs.getTimestamp("expires_at").toInstant()))
                .optional();
    }
}
