package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingInbox;
import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 24 (MI24-005) &mdash; end-to-end proof that the &sect;7.3 RLS
 * defence-in-depth is now <em>wired</em>, closing the Stage 12
 * "armed-but-not-wired" debt: the {@code V1} migration created the RLS policy
 * ({@code USING (tenant_id = current_setting('app.tenant_id', true))}, fail-closed),
 * but until Stage 24 no production code ever set {@code app.tenant_id}, so the policy
 * never activated and the application-layer {@code WHERE tenant_id = :tenantId} was
 * the <em>only</em> tenant-isolation defence. Stage 24 wraps every adapter method in
 * a short transaction that sets {@code app.tenant_id} via
 * {@code set_config('app.tenant_id', :tenantId, true)} (&equiv; {@code SET LOCAL}).
 *
 * <h2>The two-scenario proof structure (and why both are needed)</h2>
 *
 * <p>The application-layer {@code WHERE tenant_id = :tenantId} always filters first,
 * so RLS's <em>independent</em> contribution is invisible through the adapter's
 * business SQL (it is masked by the WHERE). The proof therefore works by
 * elimination, on a connection <em>bound by RLS</em> (the restricted {@code app_role},
 * reached via {@code SET ROLE app_role}):
 *
 * <p><b>Scenario B (the control) &mdash; RLS really binds {@code app_role}.</b>
 * A raw {@code SELECT count(*) FROM ...outbox} (bypassing the application WHERE) under
 * {@code app_role}: setting {@code app.tenant_id = A} shows tenant A's row (count 1),
 * setting {@code app.tenant_id = B} hides it (count 0), and leaving it unset shows
 * nothing (count 0 &mdash; fail-closed). This <em>excludes</em> the alternative
 * explanation "RLS does not actually bind app_role" &mdash; without B, a green
 * business path in A could be blamed on RLS being inert.
 *
 * <p><b>Scenario A (the smoking gun) &mdash; the adapter business path is green under
 * {@code app_role}.</b> {@code enqueue(A) &rarr; claimDue(A) &rarr; markAcked(A)} all
 * succeed and the row lands ACKED. {@code claimDue}'s {@code UPDATE ... RETURNING}
 * runs a {@code SELECT ... FOR UPDATE SKIP LOCKED} sub-query scoped by
 * {@code tenant_id = :tenantId}; under {@code app_role} that SELECT is also filtered
 * by the RLS {@code USING} clause. The INSERT in {@code enqueue} is <em>not</em>
 * RLS-checked (the policy has no {@code WITH CHECK}), so enqueue alone proves
 * nothing &mdash; but <b>{@code claimDue} returning the row</b> can only happen if
 * the RLS clause sees {@code tenant_id = current_setting('app.tenant_id', true)}
 * evaluate true, i.e. if the adapter's in-transaction {@code set_config} ran. Had the
 * adapter left {@code app.tenant_id} unset, the fail-closed policy would make the row
 * invisible and {@code claimDue} would return an empty list. So a green business path
 * under RLS <em>&hArr;</em> the wiring is live.
 *
 * <h2>Why superuser ITs are unaffected</h2>
 * <p>The seven prior C3 ITs (Stages 17&ndash;23) connect as the embedded superuser
 * (the table owner), which <em>bypasses</em> RLS &mdash; so the Stage 24
 * {@code set_config('app.tenant_id', ...)} the adapter now emits is a harmless no-op
 * for them and they stay green unchanged. This IT is the one that runs the adapter
 * under a non-bypassing role, which is the only way to observe the wiring.
 *
 * <p><b>No runtime boot.</b> Only embedded-postgres + Flyway + the JDBC adapter; the
 * A2A server is irrelevant to RLS. The {@code SET ROLE app_role} wrapper is test-only
 * plumbing that stands in for the production "restricted role" deployment model
 * (deferred: this stage wires the adapter; choosing the production role / connection
 * identity is a deployment decision).
 *
 * <p><b>&sect;6.2 unchanged &mdash; strengthened.</b> RLS is defence-in-depth for
 * tenant isolation, the opposite of "cross-tenant fallback"; {@code app.tenant_id} is
 * a session GUC, not a payload body / token stream / Task state; no concrete broker/MQ.
 *
 * <p><b>@Isolated</b> (Stage 19 finding): parent pom runs surefire 4-way concurrent
 * and these embedded-postgres ITs are not thread-safe to boot concurrently.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md}
 * &sect;7.3; {@code docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md} &sect;8 Stage 24.
 */
@Isolated
class C3ForwardingRlsWiringIntegrationTest {

    private static EmbeddedPostgres pg;
    private static DataSource superuser;
    /** DataSource whose every connection {@code SET ROLE app_role} first — bound by RLS. */
    private static DataSource appRoleSource;
    private static JdbcForwardingOutbox appRoleOutbox;
    private static JdbcForwardingInbox appRoleInbox;

    @BeforeAll
    static void bootPostgresAndRestrictedRole() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        superuser = pg.getPostgresDatabase();
        Flyway.configure().dataSource(superuser).load().migrate();

        // app_role: a non-owner role bound by the §7.3 RLS policy. The business path
        // (enqueue=INSERT, claimDue/markAcked=UPDATE+SELECT) needs DML grants — broader
        // than the SELECT-only app_role in ForwardingJdbcIntegrationTest, because this
        // IT drives the adapter's full DML path under the restricted role.
        new org.springframework.jdbc.core.JdbcTemplate(superuser).execute(
                "CREATE ROLE app_role; "
                + "GRANT USAGE ON SCHEMA public TO app_role; "
                + "GRANT SELECT, INSERT, UPDATE ON "
                + "agent_bus_forwarding_outbox, agent_bus_forwarding_inbox TO app_role");

        appRoleSource = new SetRoleDataSource(superuser, "app_role");
        appRoleOutbox = new JdbcForwardingOutbox(appRoleSource);
        appRoleInbox = new JdbcForwardingInbox(appRoleSource);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (pg != null) {
            pg.close();
        }
    }

    /**
     * Scenario A &mdash; the smoking gun that the Stage 24 wiring is live. Drives the
     * full outbox business path through the adapter <em>as {@code app_role}</em> (a
     * non-owner role bound by RLS): {@code enqueue(A)} &rarr; {@code claimDue(A)}
     * &rarr; {@code markAcked(A)}. {@code claimDue} returning the row is the proof &mdash;
     * its {@code SELECT ... FOR UPDATE SKIP LOCKED} sub-query is RLS-filtered under
     * {@code app_role}, so it could see tenant A's row only because the adapter's
     * in-transaction {@code set_config('app.tenant_id','A',true)} ran. A non-wired
     * adapter would hit fail-closed RLS and {@code claimDue} would return an empty list.
     */
    @Test
    void scenario_a_adapter_business_path_is_green_under_rls_bound_role() {
        String tenant = "tenant-rls-A";

        // INSERT is not RLS-checked (policy has only USING, no WITH CHECK), so enqueue
        // succeeding proves only that app_role has INSERT; the proof lands in claimDue.
        appRoleOutbox.enqueue(envelope(tenant, "msg-A", "route-A", Long.MAX_VALUE),
                "svc-src", "svc-tgt", System.currentTimeMillis());
        assertThat(appRoleOutbox.statusOf(id("msg-A"), tenant))
                .isEqualTo(ForwardingStatus.Outbox.PENDING);

        long now = System.currentTimeMillis();
        // THE smoking gun: the UPDATE's SELECT sub-query is RLS-filtered under app_role.
        // Returning the row ⟺ the adapter set app.tenant_id=A in this transaction.
        List<ForwardingOutboxRecord> claimed =
                appRoleOutbox.claimDue(tenant, now, 5, "owner-A", now + 60_000);
        assertThat(claimed)
                .as("claimDue saw tenant A's row under RLS — the adapter's in-tx "
                    + "set_config('app.tenant_id') must have run (else fail-closed RLS "
                    + "hides it and this list is empty)")
                .hasSize(1);
        assertThat(claimed.get(0).status()).isEqualTo(ForwardingStatus.Outbox.DISPATCHING);
        assertThat(claimed.get(0).lease().leaseOwner()).isEqualTo("owner-A");

        // markAcked's lease-guarded UPDATE is likewise RLS-filtered; success needs the
        // app.tenant_id set by the adapter's withTenant wrapper.
        assertThat(appRoleOutbox.markAcked(id("msg-A"), tenant, "owner-A"))
                .isEqualTo(ForwardingStatus.Outbox.ACKED);
        assertThat(appRoleOutbox.statusOf(id("msg-A"), tenant))
                .isEqualTo(ForwardingStatus.Outbox.ACKED);
    }

    /**
     * Scenario B (the control for A) &mdash; the &sect;7.3 RLS policy really binds
     * {@code app_role}. A raw {@code SELECT count(*) FROM ...outbox} (bypassing the
     * application WHERE) under {@code app_role}: tenant A's enqueued row is visible
     * only when {@code app.tenant_id = A}, hidden when set to another tenant, and
     * hidden when unset (fail-closed). This excludes the alternative explanation that
     * A's green path is due to RLS being inert rather than the adapter wiring.
     *
     * <p>Tenant A's row is enqueued here as the superuser (the owner) so the INSERT is
     * independent of the app_role DML path exercised in scenario A; what matters is
     * that the row exists on disk for the app_role visibility probe.
     */
    @Test
    void scenario_b_rls_policy_genuinely_filters_app_role_visibility() throws Exception {
        String tenantA = "tenant-rls-A2";
        // Enqueue as the owner so a row exists on disk for the visibility probe.
        new JdbcForwardingOutbox(superuser)
                .enqueue(envelope(tenantA, "msg-B", "route-B", Long.MAX_VALUE),
                        "svc-src", "svc-tgt", System.currentTimeMillis());

        assertThat(visibleOutboxCount("app.tenant_id", tenantA))
                .as("app_role with app.tenant_id=A sees tenant A's row")
                .isEqualTo(1);
        assertThat(visibleOutboxCount("app.tenant_id", "tenant-rls-OTHER"))
                .as("app_role with app.tenant_id=OTHER does NOT see tenant A's row (RLS filter)")
                .isZero();
        assertThat(visibleOutboxCountUnset())
                .as("app_role with app.tenant_id unset sees nothing (fail-closed)")
                .isZero();
    }

    /**
     * Inbox business path under {@code app_role} — symmetric to scenario A: the
     * dedup INSERT in {@code receive} is not RLS-checked, but {@code markConsumed}'s
     * UPDATE is RLS-filtered and needs the adapter's in-tx {@code app.tenant_id}.
     */
    @Test
    void inbox_business_path_is_green_under_rls_bound_role() {
        String tenant = "tenant-rls-inbox";
        appRoleInbox.receive(envelope(tenant, "msg-I", "route-I", Long.MAX_VALUE),
                "consumer-1", System.currentTimeMillis());
        // markConsumed's UPDATE WHERE status='RECEIVED' is RLS-filtered under app_role;
        // success ⟺ the adapter set app.tenant_id in this transaction.
        assertThat(appRoleInbox.markConsumed(id("msg-I"), tenant, "consumer-1"))
                .isEqualTo(ForwardingStatus.Inbox.CONSUMED);
    }

    // ---- visibility probes (raw SQL under app_role, bypassing the app WHERE) ----

    private static long visibleOutboxCount(String settingKey, String settingValue) throws Exception {
        try (Connection c = superuser.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SET ROLE app_role");
            st.execute("SET " + settingKey + " = '" + settingValue + "'");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM agent_bus_forwarding_outbox")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static long visibleOutboxCountUnset() throws Exception {
        try (Connection c = superuser.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SET ROLE app_role");
            // deliberately do NOT SET app.tenant_id → current_setting(...,true) is NULL → fail-closed
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM agent_bus_forwarding_outbox")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ---- helpers -----------------------------------------------------------

    private static ForwardingMessageId id(String value) {
        return new ForwardingMessageId(value);
    }

    private static ForwardingEnvelope envelope(String tenant, String messageId, String route,
                                               long deadlineMillisEpoch) {
        return new ForwardingEnvelope(
                new ForwardingMessageId(messageId), tenant, "trace-" + messageId,
                "corr-" + messageId, "idem-" + messageId,
                new ForwardingRouteHandle(route, tenant), "cap", deadlineMillisEpoch,
                ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY, null);
    }

    /**
     * Test-only {@link DataSource} wrapper that runs {@code SET ROLE <role>} on every
     * connection before handing it out, so the adapter (and its
     * {@code DataSourceTransactionManager}) operate under the restricted, RLS-bound
     * role. {@code SET ROLE} is session-scoped and idempotent, so pooling / reuse is
     * harmless. Stands in for the production "point the adapter at a restricted role"
     * deployment model.
     */
    private static final class SetRoleDataSource extends AbstractDataSource {
        private final DataSource delegate;
        private final String role;

        SetRoleDataSource(DataSource delegate, String role) {
            this.delegate = delegate;
            this.role = role;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection c = delegate.getConnection();
            try (Statement st = c.createStatement()) {
                st.execute("SET ROLE " + role);
            }
            return c;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection c = delegate.getConnection(username, password);
            try (Statement st = c.createStatement()) {
                st.execute("SET ROLE " + role);
            }
            return c;
        }
    }
}
