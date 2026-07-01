package com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc;

import com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingInboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
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
 * Postgres JDBC adapter for the C3 inbox substrate (Stage 12, MI12-002).
 *
 * <p>Implements {@link ForwardingInboxPort} against
 * {@code agent_bus_forwarding_inbox} via Spring {@link NamedParameterJdbcTemplate}
 * (an injected {@link DataSource}). The in-memory double
 * ({@code InMemoryForwardingInbox}) stays as the fast test fixture.
 *
 * <h2>SQL contract</h2>
 * <ul>
 *   <li><b>dedup</b> — {@code receive} runs {@code INSERT ... ON CONFLICT
 *       (tenant_id, message_id, consumer_service_id) DO NOTHING}; one affected row
 *       is a first arrival ({@code RECEIVED}), zero is a duplicate
 *       ({@code DUPLICATE_SUPPRESSED}, stored entry untouched, matching the
 *       in-memory contract).</li>
 *   <li><b>terminal guarded mutation</b> — {@code markConsumed} /
 *       {@code markRejected} run {@code UPDATE ... WHERE status='RECEIVED'} so only a
 *       RECEIVED row may move terminal; zero rows is diagnosed (missing vs already
 *       terminal) and classified to match the in-memory double
 *       ({@code IllegalStateException} when absent,
 *       {@code IllegalStateTransitionException} when already terminal). The next
 *       status is computed by {@link ForwardingStateMachine} before persisting.</li>
 * </ul>
 *
 * <h2>Stage 24 — transactional RLS wiring</h2>
 * <p>Every statement is scoped by {@code tenant_id = :tenantId} (application-layer
 * hard isolation, Rule R-C.c). Stage 24 also <em>wires</em> the §7.3 RLS
 * defence-in-depth: each port method runs inside a short transaction that sets the
 * transaction-scoped {@code app.tenant_id} (via
 * {@code set_config('app.tenant_id', :tenantId, true)}, equivalent to
 * {@code SET LOCAL}) before the business SQL, so a restricted (non-owner)
 * connection is filtered by tenant. The setting and the transaction auto-reset on
 * return — no connection-pool pollution. See {@link #withTenant}. The table owner
 * (superuser) bypasses RLS. This class never writes Task execution state,
 * never bypasses {@code routeHandle}, and never persists a payload body.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md}
 * §3.2 / §4 / §7.3; {@code ICD-Agent-Bus-Forwarding-Runtime}.
 */
public final class JdbcForwardingInbox implements ForwardingInboxPort {

    private static final String TABLE = "agent_bus_forwarding_inbox";

    private final NamedParameterJdbcTemplate jdbc;
    private final ForwardingStateMachine stateMachine;
    private final TransactionTemplate txTemplate;

    /**
     * Backwards-compatible constructor (Stage 12 form): derives a per-DataSource
     * {@link DataSourceTransactionManager} so every method runs inside a short
     * transaction that sets {@code app.tenant_id} (Stage 24 RLS wiring).
     */
    public JdbcForwardingInbox(DataSource dataSource) {
        this(dataSource, new DataSourceTransactionManager(dataSource));
    }

    /**
     * Full constructor (Stage 24): accepts an explicit {@link PlatformTransactionManager}
     * so production can supply a pooled / XA-aware manager. The manager must bind
     * connections from the same {@link DataSource} so the transactional connection is
     * the one {@code app.tenant_id} is set on.
     */
    public JdbcForwardingInbox(DataSource dataSource, PlatformTransactionManager txManager) {
        Objects.requireNonNull(dataSource, "dataSource is required");
        Objects.requireNonNull(txManager, "txManager is required");
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.stateMachine = new ForwardingStateMachine();
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public ForwardingStatus.Inbox receive(ForwardingEnvelope envelope, String consumerServiceId,
                                          long nowMillisEpoch) {
        Objects.requireNonNull(envelope, "envelope is required");
        Objects.requireNonNull(consumerServiceId, "consumerServiceId is required");
        if (consumerServiceId.isBlank()) {
            throw new IllegalArgumentException("consumerServiceId must not be blank");
        }
        return withTenant(envelope.tenantId(), () -> {
            String sql = "INSERT INTO " + TABLE + " ("
                    + "tenant_id, message_id, consumer_service_id, status, "
                    + "received_at, consumed_at, failure_code) "
                    + "VALUES (:tenantId, :messageId, :consumerServiceId, 'RECEIVED', :now, NULL, NULL) "
                    + "ON CONFLICT (tenant_id, message_id, consumer_service_id) DO NOTHING";
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("tenantId", envelope.tenantId())
                    .addValue("messageId", envelope.messageId().value())
                    .addValue("consumerServiceId", consumerServiceId)
                    .addValue("now", nowMillisEpoch);
            int affected = jdbc.update(sql, params);
            if (affected == 0) {
                // duplicate arrival — dedup outcome, stored entry untouched.
                return stateMachine.transitInbox(null, ForwardingStateMachine.InboxEvent.ARRIVE_DUPLICATE);
            }
            return stateMachine.transitInbox(null, ForwardingStateMachine.InboxEvent.ARRIVE_NEW);
        });
    }

    @Override
    public ForwardingStatus.Inbox markConsumed(ForwardingMessageId id, String tenantId,
                                               String consumerServiceId) {
        long now = System.currentTimeMillis();
        return withTenant(tenantId, () -> mutate(id, tenantId, consumerServiceId,
                ForwardingStateMachine.InboxEvent.CONSUME, null, now));
    }

    @Override
    public ForwardingStatus.Inbox markRejected(ForwardingMessageId id, String tenantId,
                                               String consumerServiceId, ForwardingFailureCode code) {
        Objects.requireNonNull(code, "code is required for markRejected");
        long now = System.currentTimeMillis();
        return withTenant(tenantId, () -> mutate(id, tenantId, consumerServiceId,
                ForwardingStateMachine.InboxEvent.REJECT, code, now));
    }

    @Override
    public ForwardingStatus.Inbox statusOf(ForwardingMessageId id, String tenantId,
                                           String consumerServiceId) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(consumerServiceId, "consumerServiceId is required");
        return withTenant(tenantId, () -> {
            String sql = "SELECT status FROM " + TABLE
                    + " WHERE tenant_id = :tenantId AND message_id = :messageId"
                    + " AND consumer_service_id = :consumerServiceId";
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("messageId", id.value())
                    .addValue("consumerServiceId", consumerServiceId);
            List<ForwardingStatus.Inbox> rows = jdbc.query(sql, params,
                    (rs, rowNum) -> ForwardingStatus.Inbox.valueOf(rs.getString("status")));
            if (rows.isEmpty()) {
                throw new IllegalStateException(
                        "no inbox entry for tenantId=" + tenantId + " messageId=" + id.value()
                        + " consumerServiceId=" + consumerServiceId);
            }
            return rows.get(0);
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
     * the transaction-bound connection, so the guarded UPDATE + diagnostic SELECT now
     * share one connection. The application-layer {@code WHERE tenant_id = :tenantId}
     * remains the primary isolation (Rule R-C.c); RLS is the defence-in-depth
     * fallback. A {@link RuntimeException} from {@code work} rolls the transaction
     * back and propagates.
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
     * Terminal guarded mutation. The {@code WHERE status='RECEIVED'} guard is
     * atomic; zero rows is diagnosed to match the in-memory contract — a missing
     * row raises {@code IllegalStateException}, an already-terminal row re-runs the
     * state machine against its real status to raise
     * {@code IllegalStateTransitionException}. The next status is computed from
     * {@code RECEIVED} (the only state the guard admits) before persisting.
     */
    private ForwardingStatus.Inbox mutate(ForwardingMessageId id, String tenantId,
                                          String consumerServiceId,
                                          ForwardingStateMachine.InboxEvent event,
                                          ForwardingFailureCode code, long now) {
        ForwardingStatus.Inbox next =
                stateMachine.transitInbox(ForwardingStatus.Inbox.RECEIVED, event);
        StringBuilder set = new StringBuilder("status = :next");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("next", next.name())
                .addValue("tenantId", tenantId)
                .addValue("messageId", id.value())
                .addValue("consumerServiceId", consumerServiceId);
        if (next == ForwardingStatus.Inbox.CONSUMED) {
            set.append(", consumed_at = :now, failure_code = NULL");
            params.addValue("now", now);
        } else if (next == ForwardingStatus.Inbox.REJECTED) {
            set.append(", failure_code = :failureCode");
            params.addValue("failureCode", code.wireCode());
        }
        String sql = "UPDATE " + TABLE + " SET " + set
                + " WHERE tenant_id = :tenantId AND message_id = :messageId"
                + " AND consumer_service_id = :consumerServiceId AND status = 'RECEIVED'";
        int affected = jdbc.update(sql, params);
        if (affected == 0) {
            classifyInboxFailure(id, tenantId, consumerServiceId, event);
        }
        return next;
    }

    /**
     * Diagnose a zero-row inbox UPDATE and raise to match the in-memory double:
     * missing row → {@code IllegalStateException}; present-but-not-RECEIVED row →
     * the state machine re-evaluates the (now terminal) transition and raises
     * {@code IllegalStateTransitionException}.
     */
    private void classifyInboxFailure(ForwardingMessageId id, String tenantId, String consumerServiceId,
                                      ForwardingStateMachine.InboxEvent event) {
        String sql = "SELECT status FROM " + TABLE
                + " WHERE tenant_id = :tenantId AND message_id = :messageId"
                + " AND consumer_service_id = :consumerServiceId";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("messageId", id.value())
                .addValue("consumerServiceId", consumerServiceId);
        List<ForwardingStatus.Inbox> rows = jdbc.query(sql, params,
                (rs, rowNum) -> ForwardingStatus.Inbox.valueOf(rs.getString("status")));
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "no inbox entry for tenantId=" + tenantId + " messageId=" + id.value()
                    + " consumerServiceId=" + consumerServiceId);
        }
        // Present but not RECEIVED → re-run the state machine against the real status
        // so the illegal-transition failure mode matches the in-memory double exactly.
        stateMachine.transitInbox(rows.get(0), event);
        throw new IllegalStateException(
                "inbox row for tenantId=" + tenantId + " messageId=" + id.value()
                + " consumerServiceId=" + consumerServiceId
                + " was not updated (status=" + rows.get(0) + ")");
    }
}
