package com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc;

import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingLeaseException;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-SQL integration test for the Stage 12 C3 JDBC adapter (MI12-004).
 *
 * <p>Boots an in-process PostgreSQL (Zonky embedded-postgres — the host's Docker
 * daemon can only reach registries through an authenticated proxy and there is no
 * sudo to fix it; see pom for the full rationale). Runs the agent-bus Flyway
 * migration, and drives {@link JdbcForwardingOutbox} / {@link JdbcForwardingInbox}
 * against real SQL — 兑现 forwarding-persistence §5「真实 adapter 落地后补 real-SQL
 * 集成验证」. Covers the six MI12-004 behaviours (concurrent claim, lease guard,
 * reclaim, renew-or-lose, CHECK fallback, tenant isolation) plus migration/RLS
 * correctness.
 *
 * <p>The adapter runs as the embedded superuser (the table owner, so RLS is
 * bypassed); a separate {@code app_role} connection exercises the §7.3 RLS policy
 * directly. Production deployment points the adapter at such a restricted role so
 * RLS binds.
 */
class ForwardingJdbcIntegrationTest {

    private static EmbeddedPostgres pg;
    private static JdbcForwardingOutbox outbox;
    private static JdbcForwardingInbox inbox;
    private static NamedParameterJdbcTemplate raw;
    private static DataSource dataSource;

    @BeforeAll
    static void migrateAndWire() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        dataSource = pg.getPostgresDatabase();

        Flyway.configure().dataSource(dataSource).load().migrate();

        // app_role: a non-owner role bound by RLS, used to prove the §7.3 policy.
        new NamedParameterJdbcTemplate(dataSource).getJdbcTemplate().execute(
                "CREATE ROLE app_role; "
                + "GRANT USAGE ON SCHEMA public TO app_role; "
                + "GRANT SELECT ON agent_bus_forwarding_outbox, agent_bus_forwarding_inbox TO app_role");

        outbox = new JdbcForwardingOutbox(dataSource);
        inbox = new JdbcForwardingInbox(dataSource);
        raw = new NamedParameterJdbcTemplate(dataSource);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (pg != null) {
            pg.close();
        }
    }

    // ===== migration + RLS correctness (slice 3) =====

    @Test
    void flyway_migration_creates_tables_indexes_and_rls() {
        Boolean rlsOutbox = raw.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE relname = 'agent_bus_forwarding_outbox'",
                new MapSqlParameterSource(), Boolean.class);
        Boolean rlsInbox = raw.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE relname = 'agent_bus_forwarding_inbox'",
                new MapSqlParameterSource(), Boolean.class);
        assertThat(rlsOutbox).as("RLS enabled on outbox").isTrue();
        assertThat(rlsInbox).as("RLS enabled on inbox").isTrue();

        Integer policyCount = raw.queryForObject(
                "SELECT count(*) FROM pg_policies "
                + "WHERE tablename IN ('agent_bus_forwarding_outbox','agent_bus_forwarding_inbox')",
                new MapSqlParameterSource(), Integer.class);
        assertThat(policyCount).as("one tenant-isolation policy per table").isEqualTo(2);

        Integer idxCount = raw.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_outbox_claim_due'",
                new MapSqlParameterSource(), Integer.class);
        assertThat(idxCount).as("claim-due partial index present").isEqualTo(1);
    }

    // ===== happy-path round trip =====

    @Test
    void enqueue_claim_then_ack_round_trips_status() {
        String t = tenant();
        enqueue(t, "m1");
        assertThat(outbox.statusOf(id("m1"), t)).isEqualTo(ForwardingStatus.Outbox.PENDING);

        long now = System.currentTimeMillis();
        List<ForwardingOutboxRecord> claimed = outbox.claimDue(t, now, 5, "owner-A", now + 60_000);
        assertThat(claimed).hasSize(1);
        ForwardingOutboxRecord rec = claimed.get(0);
        assertThat(rec.status()).isEqualTo(ForwardingStatus.Outbox.DISPATCHING);
        assertThat(rec.lease().leaseOwner()).isEqualTo("owner-A");
        assertThat(rec.attemptCount()).isZero();
        assertThat(rec.sourceServiceId()).isEqualTo("src");
        assertThat(rec.targetServiceId()).isEqualTo("tgt");
        assertThat(rec.routeHandle().value()).isEqualTo("route-m1");
        assertThat(rec.routeHandle().tenantScope()).isEqualTo(t);
        assertThat(outbox.statusOf(id("m1"), t)).isEqualTo(ForwardingStatus.Outbox.DISPATCHING);

        assertThat(outbox.markAcked(id("m1"), t, "owner-A")).isEqualTo(ForwardingStatus.Outbox.ACKED);
        assertThat(outbox.statusOf(id("m1"), t)).isEqualTo(ForwardingStatus.Outbox.ACKED);
    }

    // ===== MI12-004 #1: concurrent claim never duplicates (SKIP LOCKED) =====

    @Test
    void concurrent_claim_never_duplicates_a_record() throws Exception {
        String t = tenant();
        for (int i = 0; i < 20; i++) {
            enqueue(t, "c" + i);
        }
        long now = System.currentTimeMillis();
        long until = now + 60_000;

        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch start = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<List<ForwardingOutboxRecord>>> futures = new ArrayList<>();
        for (String owner : List.of("worker-1", "worker-2")) {
            futures.add(pool.submit(() -> {
                start.countDown();
                ready.await();
                return outbox.claimDue(t, now, 20, owner, until);
            }));
        }
        ready.countDown();
        start.await();
        List<ForwardingOutboxRecord> a = futures.get(0).get(30, TimeUnit.SECONDS);
        List<ForwardingOutboxRecord> b = futures.get(1).get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        List<ForwardingOutboxRecord> all = new ArrayList<>();
        all.addAll(a);
        all.addAll(b);
        assertThat(all).as("every due record claimed exactly once across both workers")
                .hasSize(20);
        HashSet<String> messageIds = new HashSet<>();
        for (ForwardingOutboxRecord r : all) {
            assertThat(messageIds.add(r.messageId().value()))
                    .as("no record claimed by both workers: " + r.messageId().value())
                    .isTrue();
        }
    }

    // ===== MI12-004 #2: lease-owner guard (stale/foreign ACK rejected) =====

    @Test
    void stale_ack_by_non_owner_is_classified_owner_mismatch() {
        String t = tenant();
        enqueue(t, "s1");
        long now = System.currentTimeMillis();
        outbox.claimDue(t, now, 5, "owner-A", now + 60_000);

        assertThatThrownBy(() -> outbox.markAcked(id("s1"), t, "owner-B"))
                .isInstanceOf(ForwardingLeaseException.class)
                .hasMessageContaining("owner mismatch")
                .extracting(e -> ((ForwardingLeaseException) e).reason())
                .isEqualTo(ForwardingLeaseException.Reason.OWNER_MISMATCH);
        // record untouched: still DISPATCHING, still owned by A
        assertThat(outbox.statusOf(id("s1"), t)).isEqualTo(ForwardingStatus.Outbox.DISPATCHING);
    }

    @Test
    void ack_of_unknown_record_is_record_not_found() {
        String t = tenant();
        assertThatThrownBy(() -> outbox.markAcked(id("ghost"), t, "owner-A"))
                .isInstanceOf(ForwardingLeaseException.class)
                .extracting(e -> ((ForwardingLeaseException) e).reason())
                .isEqualTo(ForwardingLeaseException.Reason.RECORD_NOT_FOUND);
    }

    // ===== MI12-004 #3: expired lease is reclaimable (stuck-holder reclaim) =====

    @Test
    void expired_lease_is_reclaimed_by_another_owner() {
        String t = tenant();
        enqueue(t, "r1");
        // claim with an already-expired lease (past timestamps) — simulates a
        // stalled holder whose lease_until has passed.
        long past = System.currentTimeMillis() - 10_000;
        List<ForwardingOutboxRecord> first = outbox.claimDue(t, past, 5, "stalled", past + 1);
        assertThat(first).hasSize(1);
        assertThat(first.get(0).lease().leaseOwner()).isEqualTo("stalled");

        // a second owner reclaims it via the stuck-holder path.
        long now = System.currentTimeMillis();
        List<ForwardingOutboxRecord> reclaimed = outbox.claimDue(t, now, 5, "owner-B", now + 60_000);
        assertThat(reclaimed).hasSize(1);
        assertThat(reclaimed.get(0).messageId().value()).isEqualTo("r1");
        assertThat(reclaimed.get(0).lease().leaseOwner()).isEqualTo("owner-B");

        // the stalled holder can no longer ack — the lease is owned by B now.
        assertThatThrownBy(() -> outbox.markAcked(id("r1"), t, "stalled"))
                .isInstanceOf(ForwardingLeaseException.class);
        assertThat(outbox.markAcked(id("r1"), t, "owner-B")).isEqualTo(ForwardingStatus.Outbox.ACKED);
    }

    // ===== MI12-004 #4: renew-or-lose-the-ack (§7.2 lease_until > now guard) =====

    @Test
    void renew_extends_lease_so_ack_succeeds() {
        String t = tenant();
        enqueue(t, "rn1");
        long past = System.currentTimeMillis() - 10_000;
        outbox.claimDue(t, past, 5, "owner-A", past + 1); // lease already expired

        // renew (A still the recorded owner) pushes lease_until into the future.
        long future = System.currentTimeMillis() + 120_000;
        assertThat(outbox.renewLease(id("rn1"), t, "owner-A", future)).isTrue();
        assertThat(outbox.markAcked(id("rn1"), t, "owner-A")).isEqualTo(ForwardingStatus.Outbox.ACKED);
    }

    @Test
    void ack_fails_with_no_lease_when_lease_has_expired_without_renew() {
        String t = tenant();
        enqueue(t, "rn2");
        long past = System.currentTimeMillis() - 10_000;
        outbox.claimDue(t, past, 5, "owner-A", past + 1); // expired, not renewed

        assertThatThrownBy(() -> outbox.markAcked(id("rn2"), t, "owner-A"))
                .isInstanceOf(ForwardingLeaseException.class)
                .extracting(e -> ((ForwardingLeaseException) e).reason())
                .isEqualTo(ForwardingLeaseException.Reason.NO_LEASE);
        assertThat(outbox.statusOf(id("rn2"), t)).isEqualTo(ForwardingStatus.Outbox.DISPATCHING);
    }

    // ===== release semantics: expiry makes a row reclaimable =====

    @Test
    void release_lease_expires_it_so_another_owner_can_reclaim() {
        String t = tenant();
        enqueue(t, "rel1");
        long now = System.currentTimeMillis();
        outbox.claimDue(t, now, 5, "owner-A", now + 60_000);
        assertThat(outbox.releaseLease(id("rel1"), t, "owner-A")).isTrue();
        // a second release by the same owner is a no-op (already released).
        assertThat(outbox.releaseLease(id("rel1"), t, "owner-A")).isFalse();

        // B reclaims immediately (lease_until now in the past).
        List<ForwardingOutboxRecord> reclaimed = outbox.claimDue(t, now, 5, "owner-B", now + 60_000);
        assertThat(reclaimed).hasSize(1);
        assertThat(reclaimed.get(0).lease().leaseOwner()).isEqualTo("owner-B");
        // A can no longer ack.
        assertThatThrownBy(() -> outbox.markAcked(id("rel1"), t, "owner-A"))
                .isInstanceOf(ForwardingLeaseException.class);
    }

    // ===== MI12-004 #5: CHECK fallback rejects illegal rows =====

    @Test
    void check_constraint_rejects_dispatching_without_lease() {
        String t = tenant();
        assertThatThrownBy(() -> raw.getJdbcTemplate().execute(
                "INSERT INTO agent_bus_forwarding_outbox (tenant_id, message_id, "
                + "source_service_id, target_service_id, route_handle, status, "
                + "attempt_count, created_at, updated_at) "
                + "VALUES ('" + t + "','bad1','s','tg','rh','DISPATCHING',0,1,1)"))
                .hasMessageContaining("ck_outbox_lease_status");
    }

    @Test
    void check_constraint_rejects_acked_with_failure_code() {
        String t = tenant();
        assertThatThrownBy(() -> raw.getJdbcTemplate().execute(
                "INSERT INTO agent_bus_forwarding_outbox (tenant_id, message_id, "
                + "source_service_id, target_service_id, route_handle, status, "
                + "attempt_count, created_at, updated_at, last_failure_code) "
                + "VALUES ('" + t + "','bad2','s','tg','rh','ACKED',0,1,1,'delivery_timeout')"))
                .hasMessageContaining("ck_outbox_failure_code");
    }

    // ===== MI12-004 #6: tenant isolation =====

    @Test
    void cross_tenant_claim_returns_nothing() {
        String a = tenant();
        String b = tenant();
        enqueue(a, "x1");
        long now = System.currentTimeMillis();
        // tenant B claims — must not see tenant A's record.
        List<ForwardingOutboxRecord> claimed = outbox.claimDue(b, now, 5, "owner-B", now + 60_000);
        assertThat(claimed).isEmpty();
        // tenant A's record is still claimable by A.
        assertThat(outbox.claimDue(a, now, 5, "owner-A", now + 60_000)).hasSize(1);
    }

    @Test
    void cross_tenant_statusof_is_record_not_found() {
        String a = tenant();
        String b = tenant();
        enqueue(a, "y1");
        assertThatThrownBy(() -> outbox.statusOf(id("y1"), b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no outbox entry");
    }

    // ===== §7.3 RLS enforcement (defence-in-depth tenant isolation) =====

    @Test
    void rls_policy_filters_rows_by_session_tenant_setting() throws Exception {
        String a = tenant();
        String b = tenant();
        enqueue(a, "rl1");
        enqueue(b, "rl2");

        // SET ROLE app_role binds the connection by RLS.
        assertThat(visibleOutboxCount("app.tenant_id", a)).isEqualTo(1);
        assertThat(visibleOutboxCount("app.tenant_id", b)).isEqualTo(1);
        // fail-closed: an unset tenant session sees nothing.
        assertThat(visibleOutboxCountUnset()).isZero();
    }

    // ===== inbox adapter =====

    @Test
    void inbox_receive_dedups_then_consumes() {
        String t = tenant();
        ForwardingEnvelope env = envelope(t, "i1");
        long now = System.currentTimeMillis();
        assertThat(inbox.receive(env, "consumer-1", now)).isEqualTo(ForwardingStatus.Inbox.RECEIVED);
        assertThat(inbox.receive(env, "consumer-1", now))
                .isEqualTo(ForwardingStatus.Inbox.DUPLICATE_SUPPRESSED);
        assertThat(inbox.markConsumed(id("i1"), t, "consumer-1"))
                .isEqualTo(ForwardingStatus.Inbox.CONSUMED);
        // a distinct consumer dedups independently.
        assertThat(inbox.receive(env, "consumer-2", now)).isEqualTo(ForwardingStatus.Inbox.RECEIVED);
    }

    @Test
    void inbox_mark_rejected_records_failure_code() {
        String t = tenant();
        inbox.receive(envelope(t, "i2"), "consumer-1", System.currentTimeMillis());
        assertThat(inbox.markRejected(id("i2"), t, "consumer-1", ForwardingFailureCode.TENANT_MISMATCH))
                .isEqualTo(ForwardingStatus.Inbox.REJECTED);
    }

    @Test
    void inbox_check_constraint_rejects_duplicate_without_dup_code() {
        String t = tenant();
        assertThatThrownBy(() -> raw.getJdbcTemplate().execute(
                "INSERT INTO agent_bus_forwarding_inbox (tenant_id, message_id, "
                + "consumer_service_id, status, received_at, failure_code) "
                + "VALUES ('" + t + "','ib','c','DUPLICATE_SUPPRESSED',1,'tenant_mismatch')"))
                .hasMessageContaining("ck_inbox_dup_code");
    }

    // ===== helpers =====

    private long visibleOutboxCount(String settingKey, String settingValue) throws Exception {
        try (Connection c = dataSource.getConnection();
             var st = c.createStatement()) {
            st.execute("SET ROLE app_role");
            st.execute("SET " + settingKey + " = '" + settingValue + "'");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM agent_bus_forwarding_outbox")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long visibleOutboxCountUnset() throws Exception {
        try (Connection c = dataSource.getConnection();
             var st = c.createStatement()) {
            st.execute("SET ROLE app_role");
            // deliberately do NOT SET app.tenant_id → current_setting(...,true) is NULL
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM agent_bus_forwarding_outbox")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void enqueue(String tenantId, String messageId) {
        ForwardingReceipt r = outbox.enqueue(envelope(tenantId, messageId), "src", "tgt",
                System.currentTimeMillis());
        assertThat(r.accepted()).isTrue();
    }

    private static ForwardingEnvelope envelope(String tenantId, String messageId) {
        return new ForwardingEnvelope(
                new ForwardingMessageId(messageId), AgentBusEventType.CLIENT_INVOCATION_REQUESTED,
                tenantId, "trace-" + messageId,
                "corr-" + messageId, "idem-" + messageId,
                new ForwardingRouteHandle("route-" + messageId, tenantId), "cap",
                "src-" + messageId, "tgt-" + messageId, Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY, null);
    }

    private static ForwardingMessageId id(String value) {
        return new ForwardingMessageId(value);
    }

    private static String tenant() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
