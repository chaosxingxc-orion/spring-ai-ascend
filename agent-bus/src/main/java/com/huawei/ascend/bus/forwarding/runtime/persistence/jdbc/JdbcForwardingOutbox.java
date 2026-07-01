package com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc;

import com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingLeaseException;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxClaimPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Postgres JDBC adapter for the C3 outbox substrate (Stage 12, MI12-002).
 *
 * <p>Implements {@link ForwardingOutboxPort} + {@link ForwardingOutboxClaimPort}
 * against {@code agent_bus_forwarding_outbox} via Spring
 * {@link NamedParameterJdbcTemplate} (an injected {@link DataSource}). The
 * in-memory double ({@code InMemoryForwardingOutbox}) stays as the fast test
 * fixture; this class is the real persistence.
 *
 * <h2>SQL contract</h2>
 * <ul>
 *   <li><b>claim</b> — {@code claimDue} runs the §7.1 atomic
 *       {@code UPDATE ... WHERE (...) IN (SELECT ... FOR UPDATE SKIP LOCKED)
 *       RETURNING *} so concurrent dispatchers never claim the same row; the
 *       {@code RETURNING} projection is mapped back to {@link ForwardingOutboxRecord}
 *       by {@link ForwardingSqlCodec}.</li>
 *   <li><b>lease-owner guarded mutation</b> — {@code markAcked} /
 *       {@code scheduleRetry} / {@code moveToDlq} / {@code markExpired} all run the
 *       §7.2 {@code UPDATE ... WHERE status='DISPATCHING' AND lease_owner=? AND
 *       lease_until > now} guard atomically; zero rows means a stale / foreign /
 *       expired owner and is classified into a {@link ForwardingLeaseException}
 *       via a diagnostic {@code SELECT} (record untouched). The next status is
 *       computed by {@link ForwardingStateMachine} before persisting, honouring the
 *       SPI "validate the transition before persisting" rule.</li>
 * </ul>
 *
 * <h2>releaseLease divergence from the in-memory double</h2>
 * <p>The DDL {@code CHECK ck_outbox_lease_status} forces a {@code DISPATCHING} row
 * to carry a non-null {@code lease_owner}. The in-memory double's
 * {@code releaseLease} nulls the lease field, which is legal for its internal
 * mutable row but would violate that CHECK here. This adapter therefore releases
 * by <em>expiring</em> the lease ({@code lease_until = RELEASED_LEASE_UNTIL}),
 * leaving the row CHECK-valid yet immediately reclaimable by another owner via the
 * §7.1 {@code status='DISPATCHING' AND lease_until <= now} stuck-holder path. The
 * behavioural contract (release ⇒ another owner may reclaim; the releaser can no
 * longer mutate) holds; the storage representation differs. Recorded as a Stage 12
 * finding in {@code forwarding-persistence.md §7.2}.
 *
 * <h2>Stage 24 — transactional RLS wiring</h2>
 * <p>Tenant isolation: every statement scopes by {@code tenant_id = :tenantId}
 * (application-layer hard isolation, Rule R-C.c). Stage 24 also <em>wires</em> the
 * §7.3 RLS defence-in-depth: each port method runs inside a short transaction that
 * sets the transaction-scoped {@code app.tenant_id} (via
 * {@code set_config('app.tenant_id', :tenantId, true)}, equivalent to
 * {@code SET LOCAL}) before the business SQL, so a restricted (non-owner)
 * connection is filtered by tenant. The setting and the transaction auto-reset on
 * return — no connection-pool pollution. See {@link #withTenant}. The table owner
 * (superuser) bypasses RLS, so superuser-backed integration tests are unaffected.
 *
 * <p>This class never writes Task execution state, never bypasses
 * {@code routeHandle} to a physical endpoint, and never persists a payload body.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md}
 * §4 / §7.1 / §7.2 / §7.3; {@code ICD-Agent-Bus-Forwarding-Runtime}.
 */
public final class JdbcForwardingOutbox implements ForwardingOutboxPort, ForwardingOutboxClaimPort {

    /**
     * Sentinel {@code lease_until} stamped by {@link #releaseLease} to expire the
     * lease without clearing {@code lease_owner} (which the
     * {@code ck_outbox_lease_status} CHECK forbids for a {@code DISPATCHING} row).
     * Any non-negative {@code now} reads it as expired, so the row re-enters the
     * {@code claimDue} stuck-holder reclaim path.
     */
    static final long RELEASED_LEASE_UNTIL = -1L;

    private static final String TABLE = "agent_bus_forwarding_outbox";

    private final NamedParameterJdbcTemplate jdbc;
    private final ForwardingStateMachine stateMachine;
    private final TransactionTemplate txTemplate;

    /**
     * Backwards-compatible constructor (Stage 12 form): derives a per-DataSource
     * {@link DataSourceTransactionManager} so every method runs inside a short
     * transaction that sets {@code app.tenant_id} (Stage 24 RLS wiring).
     */
    public JdbcForwardingOutbox(DataSource dataSource) {
        this(dataSource, new DataSourceTransactionManager(dataSource));
    }

    /**
     * Full constructor (Stage 24): accepts an explicit {@link PlatformTransactionManager}
     * so production can supply a pooled / XA-aware manager, and tests can inject a
     * controlled one. The manager must bind connections from the same {@link DataSource}
     * so the transactional connection is the one {@code app.tenant_id} is set on.
     */
    public JdbcForwardingOutbox(DataSource dataSource, PlatformTransactionManager txManager) {
        Objects.requireNonNull(dataSource, "dataSource is required");
        Objects.requireNonNull(txManager, "txManager is required");
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.stateMachine = new ForwardingStateMachine();
        this.txTemplate = new TransactionTemplate(txManager);
    }

    // ===== ForwardingOutboxPort =====

    @Override
    public ForwardingReceipt enqueue(ForwardingEnvelope envelope, String sourceServiceId,
                                     String targetServiceId, long nowMillisEpoch) {
        Objects.requireNonNull(envelope, "envelope is required");
        requireNonBlank(sourceServiceId, "sourceServiceId");
        requireNonBlank(targetServiceId, "targetServiceId");
        ForwardingStatus.Outbox status =
                stateMachine.transitOutbox(null, ForwardingStateMachine.OutboxEvent.ENQUEUE);
        return withTenant(envelope.tenantId(), () -> {
            String sql = "INSERT INTO " + TABLE + " ("
                    + "tenant_id, message_id, source_service_id, target_service_id, route_handle, "
                    + "payload_ref, status, attempt_count, next_attempt_at, created_at, updated_at, "
                    + "last_failure_code, lease_owner, lease_until) "
                    + "VALUES (:tenantId, :messageId, :sourceServiceId, :targetServiceId, :routeHandle, "
                    + ":payloadRef, :status, 0, NULL, :now, :now, NULL, NULL, NULL) "
                    + "ON CONFLICT (tenant_id, message_id) DO NOTHING";
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("tenantId", envelope.tenantId())
                    .addValue("messageId", envelope.messageId().value())
                    .addValue("sourceServiceId", sourceServiceId)
                    .addValue("targetServiceId", targetServiceId)
                    .addValue("routeHandle", envelope.routeHandle().value())
                    .addValue("payloadRef", envelope.payloadRef())
                    .addValue("status", status.name())
                    .addValue("now", nowMillisEpoch);
            jdbc.update(sql, params);
            // idempotent re-enqueue (ON CONFLICT DO NOTHING) still returns accepted.
            return ForwardingReceipt.accepted(envelope.messageId(), envelope.tenantId(), nowMillisEpoch);
        });
    }

    @Override
    public ForwardingStatus.Outbox markAcked(ForwardingMessageId id, String tenantId, String leaseOwner) {
        return withTenant(tenantId, () -> leaseGuardedUpdate(id, tenantId, leaseOwner,
                ForwardingStateMachine.OutboxEvent.ACK,
                (params, set) -> set.append(", last_failure_code = NULL")
                        .append(", lease_owner = NULL, lease_until = NULL")));
    }

    @Override
    public ForwardingStatus.Outbox scheduleRetry(ForwardingMessageId id, String tenantId, String leaseOwner,
                                                  ForwardingFailureCode code, long nextAttemptAtMillisEpoch) {
        Objects.requireNonNull(code, "code is required for scheduleRetry");
        if (!code.retryable()) {
            throw new IllegalArgumentException(
                    "scheduleRetry requires a retryable failureCode; " + code
                    + " is not retryable (MI9-004)");
        }
        return withTenant(tenantId, () -> leaseGuardedUpdate(id, tenantId, leaseOwner,
                ForwardingStateMachine.OutboxEvent.RETRY,
                (params, set) -> {
                    set.append(", next_attempt_at = :nextAttemptAt")
                            .append(", attempt_count = attempt_count + 1")
                            .append(", last_failure_code = :failureCode")
                            .append(", lease_owner = NULL, lease_until = NULL");
                    params.addValue("nextAttemptAt", nextAttemptAtMillisEpoch);
                    params.addValue("failureCode", code.wireCode());
                }));
    }

    @Override
    public ForwardingStatus.Outbox moveToDlq(ForwardingMessageId id, String tenantId, String leaseOwner,
                                              ForwardingFailureCode code) {
        Objects.requireNonNull(code, "code is required for moveToDlq");
        if (code.dedup()) {
            throw new IllegalArgumentException(
                    "moveToDlq must not carry a dedup failureCode (MI9-004)");
        }
        return withTenant(tenantId, () -> leaseGuardedUpdate(id, tenantId, leaseOwner,
                ForwardingStateMachine.OutboxEvent.EXHAUST_RETRIES,
                (params, set) -> {
                    set.append(", last_failure_code = :failureCode")
                            .append(", lease_owner = NULL, lease_until = NULL");
                    params.addValue("failureCode", code.wireCode());
                }));
    }

    @Override
    public ForwardingStatus.Outbox markExpired(ForwardingMessageId id, String tenantId, String leaseOwner) {
        return withTenant(tenantId, () -> leaseGuardedUpdate(id, tenantId, leaseOwner,
                ForwardingStateMachine.OutboxEvent.EXPIRE,
                (params, set) -> set.append(", last_failure_code = 'delivery_timeout'")
                        .append(", lease_owner = NULL, lease_until = NULL")));
    }

    @Override
    public ForwardingStatus.Outbox statusOf(ForwardingMessageId id, String tenantId) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        return withTenant(tenantId, () -> {
            String sql = "SELECT status FROM " + TABLE
                    + " WHERE tenant_id = :tenantId AND message_id = :messageId";
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("messageId", id.value());
            List<ForwardingStatus.Outbox> rows = jdbc.query(sql, params,
                    (rs, rowNum) -> ForwardingStatus.Outbox.valueOf(rs.getString("status")));
            if (rows.isEmpty()) {
                throw new IllegalStateException(
                        "no outbox entry for tenantId=" + tenantId + " messageId=" + id.value());
            }
            return rows.get(0);
        });
    }

    // ===== ForwardingOutboxClaimPort =====

    @Override
    public List<ForwardingOutboxRecord> claimDue(String tenantId, long nowMillisEpoch, int limit,
                                                  String leaseOwner, long leaseUntilMillisEpoch) {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(leaseOwner, "leaseOwner");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        if (leaseUntilMillisEpoch <= nowMillisEpoch) {
            throw new IllegalArgumentException("leaseUntilMillisEpoch must be > nowMillisEpoch");
        }
        return withTenant(tenantId, () -> {
            // §7.1: atomically claim due, tenant-scoped, non-terminal rows whose lease is
            // free or expired, transition to DISPATCHING, stamp the exclusive lease, and
            // RETURN the updated rows. FOR UPDATE SKIP LOCKED lets concurrent dispatchers
            // skip rows a peer already locked.
            String sql = "UPDATE " + TABLE + " AS o "
                    + "SET status = 'DISPATCHING', "
                    + "    lease_owner = :leaseOwner, "
                    + "    lease_until = :leaseUntil, "
                    + "    updated_at = :now "
                    + "WHERE (o.tenant_id, o.message_id) IN ("
                    + "    SELECT tenant_id, message_id FROM " + TABLE
                    + "    WHERE tenant_id = :tenantId "
                    + "      AND (status = 'PENDING' "
                    + "           OR (status = 'RETRY_SCHEDULED' AND next_attempt_at <= :now) "
                    + "           OR (status = 'DISPATCHING' AND lease_until <= :now)) "
                    + "    ORDER BY next_attempt_at NULLS FIRST "
                    + "    LIMIT :limit "
                    + "    FOR UPDATE SKIP LOCKED"
                    + ") RETURNING *";
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("leaseOwner", leaseOwner)
                    .addValue("leaseUntil", leaseUntilMillisEpoch)
                    .addValue("now", nowMillisEpoch)
                    .addValue("tenantId", tenantId)
                    .addValue("limit", limit);
            return jdbc.query(sql, params, ForwardingSqlCodec.OUTBOX_ROW_MAPPER);
        });
    }

    @Override
    public boolean renewLease(ForwardingMessageId id, String tenantId, String leaseOwner,
                              long leaseUntilMillisEpoch) {
        requireNonBlank(leaseOwner, "leaseOwner");
        return withTenant(tenantId, () -> {
            // Matches the in-memory double: renew succeeds iff the caller is the current
            // DISPATCHING owner — it does NOT require the lease to be unexpired (a holder
            // that has not yet been reclaimed may extend a soon-to-expire lease).
            String sql = "UPDATE " + TABLE + " SET lease_until = :leaseUntil "
                    + "WHERE tenant_id = :tenantId AND message_id = :messageId "
                    + "AND status = 'DISPATCHING' AND lease_owner = :leaseOwner";
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("leaseUntil", leaseUntilMillisEpoch)
                    .addValue("tenantId", tenantId)
                    .addValue("messageId", id.value())
                    .addValue("leaseOwner", leaseOwner);
            return jdbc.update(sql, params) > 0;
        });
    }

    @Override
    public boolean releaseLease(ForwardingMessageId id, String tenantId, String leaseOwner) {
        requireNonBlank(leaseOwner, "leaseOwner");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(id, "id is required");
        return withTenant(tenantId, () -> {
            // Expire the lease (see class javadoc): leaves the row CHECK-valid
            // (DISPATCHING ⇒ lease_owner NOT NULL) while making it reclaimable. A second
            // release on an already-released row (lease_until <= RELEASED_LEASE_UNTIL)
            // is a no-op → false, matching the in-memory "already released" outcome.
            String sql = "UPDATE " + TABLE + " SET lease_until = :released "
                    + "WHERE tenant_id = :tenantId AND message_id = :messageId "
                    + "AND status = 'DISPATCHING' AND lease_owner = :leaseOwner "
                    + "AND lease_until > :released";
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("released", RELEASED_LEASE_UNTIL)
                    .addValue("tenantId", tenantId)
                    .addValue("messageId", id.value())
                    .addValue("leaseOwner", leaseOwner);
            return jdbc.update(sql, params) > 0;
        });
    }

    // ===== internals =====

    /**
     * Stage 24 RLS wiring: run {@code work} inside a short transaction after setting
     * the transaction-scoped {@code app.tenant_id} (PostgreSQL
     * {@code set_config('app.tenant_id', :tenantId, true)} ≡ {@code SET LOCAL},
     * bound as a named parameter to prevent tenantId injection). This binds the §7.3
     * RLS policy for the duration of the work so a restricted (non-owner) connection
     * is filtered by tenant; the setting and the transaction auto-reset on return, so
     * there is no connection-pool pollution. Every statement in {@code work} reuses
     * the transaction-bound connection, so the lease-guarded UPDATE + diagnostic
     * SELECT now share one connection (a Stage 24 side-benefit over the prior
     * auto-commit per-statement model). The application-layer
     * {@code WHERE tenant_id = :tenantId} remains the primary isolation (Rule R-C.c);
     * RLS is the defence-in-depth fallback. A {@link RuntimeException} from
     * {@code work} (e.g. {@link ForwardingLeaseException}) rolls the transaction back
     * and propagates.
     */
    private <T> T withTenant(String tenantId, Supplier<T> work) {
        return txTemplate.execute(status -> {
            // set_config returns the set value (text); queryForObject consumes the row
            // (jdbc.update would reject a returned result set). The value is discarded.
            jdbc.queryForObject("SELECT set_config('app.tenant_id', :tenantId, true)",
                    new MapSqlParameterSource("tenantId", tenantId), String.class);
            return work.get();
        });
    }

    /**
     * §7.2 lease-owner guarded UPDATE. The {@code WHERE} guard (DISPATCHING +
     * current owner + unexpired lease) is atomic; zero rows means the caller is
     * not the current holder and the row was untouched — classified by a
     * diagnostic SELECT. The {@code setBuilder} appends the event-specific SET
     * fragments (terminal clears the lease; RETRY bumps attempt / sets next attempt).
     */
    private ForwardingStatus.Outbox leaseGuardedUpdate(ForwardingMessageId id, String tenantId,
                                                        String leaseOwner,
                                                        ForwardingStateMachine.OutboxEvent event,
                                                        SetFragmentBuilder setBuilder) {
        requireNonBlank(leaseOwner, "leaseOwner");
        // SPI contract: validate the transition via the state machine before persisting.
        // The WHERE guard below guarantees current == DISPATCHING for any updated row,
        // so transitOutbox(DISPATCHING, event) yields the legal next status that this
        // UPDATE persists.
        ForwardingStatus.Outbox next = stateMachine.transitOutbox(
                ForwardingStatus.Outbox.DISPATCHING, event);
        long now = System.currentTimeMillis();
        StringBuilder set = new StringBuilder("status = :next, updated_at = :now");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("next", next.name())
                .addValue("now", now)
                .addValue("tenantId", tenantId)
                .addValue("messageId", id.value())
                .addValue("leaseOwner", leaseOwner);
        setBuilder.build(params, set);
        String sql = "UPDATE " + TABLE + " SET " + set
                + " WHERE tenant_id = :tenantId AND message_id = :messageId"
                + " AND status = 'DISPATCHING' AND lease_owner = :leaseOwner"
                + " AND lease_until > :now";
        int affected = jdbc.update(sql, params);
        if (affected == 0) {
            throw classifyLeaseFailure(id, tenantId, leaseOwner, now);
        }
        return next;
    }

    /**
     * Diagnose a zero-row §7.2 guard miss and raise the matching
     * {@link ForwardingLeaseException.Reason} (record untouched). Tenant scope is
     * preserved by the {@code tenant_id = :tenantId} filter — a cross-tenant lookup
     * reads as RECORD_NOT_FOUND, never a cross-tenant row.
     */
    private ForwardingLeaseException classifyLeaseFailure(ForwardingMessageId id, String tenantId,
                                                          String leaseOwner, long now) {
        String sql = "SELECT status, lease_owner, lease_until FROM " + TABLE
                + " WHERE tenant_id = :tenantId AND message_id = :messageId";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("messageId", id.value());
        List<RowState> rows = jdbc.query(sql, params, (rs, rowNum) -> new RowState(
                rs.getString("status"),
                rs.getString("lease_owner"),
                rs.getLong("lease_until"),
                rs.wasNull()));
        if (rows.isEmpty()) {
            return new ForwardingLeaseException(ForwardingLeaseException.Reason.RECORD_NOT_FOUND,
                    "no outbox entry for tenantId=" + tenantId + " messageId=" + id.value());
        }
        RowState row = rows.get(0);
        if (!"DISPATCHING".equals(row.status)) {
            return new ForwardingLeaseException(ForwardingLeaseException.Reason.NOT_DISPATCHING,
                    "record not DISPATCHING (status=" + row.status + ") for tenantId=" + tenantId
                    + " messageId=" + id.value());
        }
        if (row.leaseOwner == null || row.leaseUntilWasNull || row.leaseUntil <= now) {
            return new ForwardingLeaseException(ForwardingLeaseException.Reason.NO_LEASE,
                    "no active lease on tenantId=" + tenantId + " messageId=" + id.value()
                    + " (expired or cleared)");
        }
        // DISPATCHING, lease present and unexpired, but owned by someone else.
        return new ForwardingLeaseException(ForwardingLeaseException.Reason.OWNER_MISMATCH,
                "lease owner mismatch on tenantId=" + tenantId + " messageId=" + id.value()
                + ": held by " + row.leaseOwner + ", caller " + leaseOwner);
    }

    /** Appends event-specific SET fragments to a guarded UPDATE. */
    @FunctionalInterface
    private interface SetFragmentBuilder {
        void build(MapSqlParameterSource params, StringBuilder set);
    }

    /** Diagnostic snapshot of a single outbox row for lease-failure classification. */
    private record RowState(String status, String leaseOwner, long leaseUntil, boolean leaseUntilWasNull) {}

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
