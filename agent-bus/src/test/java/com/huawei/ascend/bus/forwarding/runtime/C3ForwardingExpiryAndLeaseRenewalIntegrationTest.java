package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 22 (MI22-001 / MI22-002) &mdash; end-to-end verification of the two
 * time-driven terminal / renewal paths the prior stages verified only at the
 * unit / contract layer or left as a SQL-correct-but-untriggered path, closing
 * the last two blind spots of the C3 dispatch loop's <em>time</em> surface.
 *
 * <p>Stages 17&ndash;21 closed the three end-to-end lifelines (retry round-trip,
 * lease reclaim, circuit breaker, single- and multi-worker) but recorded two
 * time-driven paths as still-unverified end to end:
 * <ol>
 *   <li><b>The EXPIRED terminal state end to end.</b>
 *       {@link ForwardingDispatcherWorker}'s {@code case EXPIRED} branch calls
 *       {@link JdbcForwardingOutbox#markExpired}, a lease-guarded UPDATE that
 *       flips a DISPATCHING row to EXPIRED and clears its lease &mdash; but it was
 *       verified only by a contract test against the in-memory harness, never on
 *       real SQL through the worker. The third terminal state (after ACKED =
 *       Stage 17, DLQ = Stage 18) had no end-to-end proof that the persisted row
 *       actually lands EXPIRED with the right invariant (a non-null
 *       {@code last_failure_code}, a released lease) and is then never reclaimed.</li>
 *   <li><b>Lease renewal actually firing end to end.</b>
 *       MI11-001 wired a renewal check into the worker (before Stage 11 the check
 *       read the tick-start instant, so the remainder never shrank inside a tick
 *       and renewal could not fire under a natural dispatch loop). The fix reads
 *       the live injected clock; but whether a real {@code claimDue}&rarr;
 *       {@code renewLease}&rarr;{@code deliver} tick actually <em>renews</em> a
 *       short lease before delivery, on real persistence, was deferred. Stage 20
 *       used {@link ForwardingDispatcherWorker.DispatchLeasePolicy#DISABLED}
 *       throughout, so renewal never fired in any end-to-end IT.</li>
 * </ol>
 *
 * <p>Stage 22 closes both, on REAL persistence (embedded-postgres + Flyway +
 * {@link JdbcForwardingOutbox}). Two scenarios:
 * <pre>
 *   A. EXPIRED terminal state end to end
 *        enqueue PENDING &rarr; MutableEpochClock(t0) + fake delivery returning
 *          expired() + DISABLED lease + ALWAYS_CLOSED breaker &rarr; one tick &rarr;
 *          markExpired (lease-guarded UPDATE) &rarr; persisted EXPIRED.
 *        expired=1, attempt_count=0 (EXPIRED is terminal, never retried),
 *        last_failure_code='delivery_timeout' (markExpired's hard-coded code),
 *        lease_owner/lease_until = NULL (lease released). A second tick then
 *        claims NOTHING for this row &mdash; EXPIRED is terminal, it is not in
 *        claimDue's candidate set (PENDING / RETRY_SCHEDULED / DISPATCHING), so it
 *        is never reclaimed. Aggregate claimed=1 across 2 ticks (not 2).
 *
 *   B. lease renewal actually firing end to end
 *        enqueue PENDING &rarr; MutableEpochClock(t0) + leaseDuration=30s (claim
 *          sets lease_until=t0+30s) + DispatchLeasePolicy(renewBefore=50s,
 *          extend=60s) + an OBSERVING delivery port that, at deliver time, reads
 *          the live PG row and asserts lease_until==t0+90s (the renewed value)
 *          and lease_owner==this worker, then acks &rarr; one tick &rarr; the
 *          renewal check (remaining = (t0+30s) - t0 = 30s &lt; 50s) fires
 *          renewLease(msg, t0+90s) BEFORE deliver &rarr; deliver observes the
 *          renewed lease &rarr; markAcked &rarr; ACKED.
 *        acked=1, skipped=0 (the renew succeeded), renewalObserved=true (the
 *          observing port saw the extended lease at deliver time), attempt_count=0.
 * </pre>
 *
 * <p><b>Key finding (scenario A): EXPIRED is SQL-correct but trigger-source-missing.</b>
 * The {@link com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord} does
 * not persist a deadline field (the {@link ForwardingEnvelope} carries
 * {@code deadlineMillisEpoch}, but it is not projected onto the persisted row),
 * and {@link com.huawei.ascend.bus.forwarding.runtime.transport.a2a.A2aForwardingDeliveryPort#deliver}
 * does not consult the deadline &mdash; so a real A2A delivery never returns
 * {@link ForwardingDeliveryResult#expired()}. The EXPIRED path is therefore
 * exercised here by an <em>injected</em> {@code expired()} outcome, exactly as
 * Stage 18 injected {@code dlq(...)} for the DLQ path. This proves the
 * {@code markExpired} SQL contract is correct (EXPIRED lands with the right
 * invariant and is terminal), but the <em>real trigger source</em> &mdash; a
 * deadline field on the record + a DDL column + a deliver-time deadline check
 * &mdash; is recorded as deferred (a schema change, out of scope for this
 * verification stage). The hard-coded {@code last_failure_code='delivery_timeout'}
 * inside {@code markExpired} satisfies the record invariant (EXPIRED requires a
 * non-null {@code last_failure_code}) and is a retryable code, which is
 * semantically muddled but harmless: EXPIRED is terminal, so the code is never
 * consulted for a retry decision.
 *
 * <p><b>Scenario B is pure arithmetic (the MI11-001 design dividend).</b> Because
 * the renewal check reads the <em>injected</em> {@link EpochClock}, not the real
 * wall clock, freezing the clock at {@code T0} and claiming with a short lease
 * ({@code lease_until = T0+30s}) makes {@code remaining = 30s} &mdash; below the
 * 50s threshold &mdash; without any sleep. The renewal thus fires deterministically
 * on the very first tick, CI-stable. The smoking gun that renewal <em>really</em>
 * happened is the observing delivery port reading the renewed
 * {@code lease_until = T0+90s} from PG <em>at deliver time</em> &mdash; a value
 * that could only exist if {@code renewLease} committed its UPDATE before
 * {@code deliver} ran.
 *
 * <p><b>Real-clock guard constraint.</b> {@link JdbcForwardingOutbox}'s
 * lease-guarded mutations ({@code markExpired} / {@code markAcked}) gate on
 * {@code lease_until > System.currentTimeMillis()} using the <b>real</b> wall
 * clock, not the injected one. So the tick instants start at {@code T0} (the
 * test's real start moment) and the claim lease ({@code T0+60s} in A,
 * {@code T0+30s}&rarr;renewed {@code T0+90s} in B) always exceeds the real clock,
 * so the lease guard never misfires. This IT touches no production code; the
 * real-clock lease semantics stay as-is.
 *
 * <p><b>No runtime boot.</b> Neither scenario needs a real A2A server: scenario A
 * injects the {@code expired()} outcome; scenario B injects {@code acked()} via
 * an observing port. So this IT boots only embedded-postgres + Flyway + the
 * outbox &mdash; lighter than Stage 17 / 18. The EXPIRED / renewal behaviour is
 * REAL (real SQL {@code markExpired} / {@code renewLease}); only the delivery
 * outcome is injected.
 *
 * <p><b>&sect;6.2 unchanged.</b> Both scenarios use a {@code CONTROL_ONLY}
 * envelope; the time-control helpers / observing port are test-scope plain-JDK
 * &mdash; no concrete broker/MQ, no payload body / token stream / Task state, no
 * cross-tenant fallback.
 *
 * <p><b>@Isolated</b> (Stage 19 finding): the parent pom runs surefire 4-way
 * concurrent, and these context-boot ITs match the surefire (not failsafe) name
 * pattern, so they would otherwise land on the concurrent runner; Spring Boot 4's
 * {@code SpringApplication.run} is not thread-safe, so concurrent boot flaked
 * ({@code ConcurrentModificationException}). {@code @Isolated} makes each run
 * exclusive, and these ITs share the embedded-postgres boot recipe.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage21-review-and-stage22-plan.md}
 * &sect;2 / &sect;4 MI22-001 / MI22-002.
 */
@Isolated
class C3ForwardingExpiryAndLeaseRenewalIntegrationTest {

    private static EmbeddedPostgres pg;
    private static DataSource dataSource;
    private static JdbcForwardingOutbox outbox;

    @BeforeAll
    static void bootPostgres() throws Exception {
        // Real PostgreSQL only — no agent-runtime server boot (scenario A injects
        // expired(); scenario B injects acked() via an observing port). Lighter
        // than Stage 17/18.
        pg = EmbeddedPostgres.builder().start();
        dataSource = pg.getPostgresDatabase();
        Flyway.configure().dataSource(dataSource).load().migrate();
        outbox = new JdbcForwardingOutbox(dataSource);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (pg != null) {
            pg.close();
        }
    }

    /**
     * Scenario A &mdash; the EXPIRED terminal state, verified end to end on real
     * persistence, closing the third terminal state (ACKED = Stage 17, DLQ =
     * Stage 18, EXPIRED = here). A single dispatch tick drives a DISPATCHING row
     * to EXPIRED via {@code markExpired}, proving the lease-guarded UPDATE lands
     * the right on-disk invariant and that EXPIRED is then never reclaimed.
     *
     * <p>The delivery outcome is injected as {@link ForwardingDeliveryResult#expired()}
     * because, per the Stage 22 key finding, no real delivery path returns
     * {@code expired()} today (the record persists no deadline and the A2A
     * delivery port does not consult one). This proves the {@code markExpired}
     * SQL contract end to end while the real trigger source stays deferred. The
     * assertions prove:
     * <ul>
     *   <li>{@code expired==1} and the persisted {@code status==EXPIRED} with
     *       {@code last_failure_code=='delivery_timeout'} (markExpired's
     *       hard-coded code) and a <b>released lease</b>
     *       ({@code lease_owner}/{@code lease_until} both NULL) &mdash; the EXPIRED
     *       record invariant (terminal state + non-null failure code + no lease);</li>
     *   <li>{@code attempt_count==0} &mdash; EXPIRED is terminal, never a retry;</li>
     *   <li>across <b>two</b> ticks the aggregate {@code claimed==1} (not 2) &mdash;
     *       the second tick claims nothing for this row, because EXPIRED is not in
     *       {@code claimDue}'s candidate set (PENDING / RETRY_SCHEDULED /
     *       DISPATCHING), so a terminal EXPIRED row is never reclaimed.</li>
     * </ul>
     */
    @Test
    void scenario_a_expired_terminal_state_end_to_end() throws Exception {
        String tenant = "tenant-expired";
        String route = "route-expired";
        String messageId = "msg-expired";
        long t0 = System.currentTimeMillis();

        // deadline = t0 - 1s reflects the SEMANTICS of a message whose processing
        // deadline has already passed; the value is not projected onto the
        // persisted row and the A2A port does not consult it, so EXPIRED is
        // triggered here by the injected expired() outcome, not the deadline.
        // (See the Stage 22 key finding: the real trigger source is deferred.)
        outbox.enqueue(envelope(tenant, messageId, route, t0 - 1_000),
                "svc-src", "svc-tgt", t0);
        assertThat(outbox.statusOf(id(messageId), tenant))
                .isEqualTo(ForwardingStatus.Outbox.PENDING);

        MutableEpochClock clock = new MutableEpochClock(t0);
        ForwardingDeliveryPort delivery = fakeDeliveryPort(List.of(ForwardingDeliveryResult.expired()));
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, delivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                clock, ForwardingRetryPolicy.DEFAULT);

        // 2 ticks at t0, t0+61s. Tick 1 claims the PENDING row, delivers expired()
        // → markExpired → EXPIRED. Tick 2 finds the row EXPIRED (terminal, not in
        // claimDue's candidate set) → claims nothing for it.
        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                worker, advanceableTickSource(clock, t0, 61_000, 2),
                ForwardingDispatchLoop.NO_BACKOFF);
        ForwardingDispatcherWorker.DispatchTickResult tick =
                loop.run(tenant, 5, "worker-exp", 60_000);

        assertThat(tick.expired())
                .as("the single due row was driven to EXPIRED by markExpired")
                .isEqualTo(1);
        assertThat(tick.acked()).isZero();
        assertThat(tick.retried()).isZero();
        assertThat(tick.dlqd()).isZero();
        assertThat(tick.skipped()).isZero();
        assertThat(tick.claimed())
                .as("EXPIRED is terminal: across 2 ticks only the first claimed the row "
                    + "(the second tick claims nothing for an EXPIRED row)")
                .isEqualTo(1);
        assertThat(tick.claimed())
                .as("tick counts stay self-consistent")
                .isEqualTo(tick.acked() + tick.retried() + tick.dlqd() + tick.expired() + tick.skipped());

        assertThat(outbox.statusOf(id(messageId), tenant))
                .as("markExpired persisted the row as EXPIRED")
                .isEqualTo(ForwardingStatus.Outbox.EXPIRED);
        Map<String, Object> row = outboxFullRow(tenant, messageId);
        assertThat(row.get("status")).isEqualTo("EXPIRED");
        assertThat(row.get("last_failure_code"))
                .as("markExpired hard-codes last_failure_code='delivery_timeout' "
                    + "(satisfies the EXPIRED record invariant: a non-null failure code)")
                .isEqualTo("delivery_timeout");
        assertThat(row.get("attempt_count"))
                .as("EXPIRED is terminal — never a retry, attempt_count untouched")
                .isEqualTo(0);
        assertThat(row.get("lease_owner"))
                .as("markExpired releases the lease (lease_owner cleared)")
                .isNull();
        assertThat(row.get("lease_until"))
                .as("markExpired releases the lease (lease_until cleared)")
                .isNull();
    }

    /**
     * Scenario B &mdash; lease renewal actually firing end to end on real
     * persistence, closing the MI11-001 / Stage 10 deferred "renewal real-elapsed
     * verification" item. A short claimed lease ({@code lease_until = T0+30s})
     * plus a {@code DispatchLeasePolicy(renewBefore=50s, extend=60s)} makes the
     * renewal check fire on the very first tick (remaining = 30s &lt; 50s), and
     * an <em>observing</em> delivery port reads the live PG row at deliver time
     * to prove the lease was renewed to {@code T0+90s} <em>before</em> delivery.
     *
     * <p>The renewal is pure arithmetic (the MI11-001 design dividend): the check
     * reads the injected clock, so freezing the clock at {@code T0} and claiming
     * a short lease deterministically drives {@code remaining} below the threshold
     * without any sleep. The assertions prove:
     * <ul>
     *   <li>{@code renewalObserved==true} &mdash; at deliver time the live PG row
     *       had {@code lease_until == T0+90s} (the renewed value, = claim lease
     *       T0+30s + 60s extension) and {@code lease_owner == this worker}; that
     *       value could only exist if {@code renewLease} committed its UPDATE
     *       before {@code deliver} ran &mdash; the smoking gun that renewal fired;</li>
     *   <li>{@code acked==1} and {@code skipped==0} &mdash; the renew succeeded
     *       (returned true) so the record was delivered and acked, not skipped;</li>
     *   <li>the persisted row is {@code ACKED} with {@code attempt_count==0}.</li>
     * </ul>
     */
    @Test
    void scenario_b_lease_renewal_fires_end_to_end() throws Exception {
        String tenant = "tenant-renew";
        String route = "route-renew";
        String messageId = "msg-renew";
        String leaseOwner = "worker-renew";
        long t0 = System.currentTimeMillis();

        outbox.enqueue(envelope(tenant, messageId, route, Long.MAX_VALUE),
                "svc-src", "svc-tgt", t0);

        MutableEpochClock clock = new MutableEpochClock(t0);
        long claimLeaseDurationMillis = 30_000;   // claim sets lease_until = t0 + 30s
        long expectedRenewedLeaseUntil = t0 + 90_000; // = (t0+30s) + 60s extension
        AtomicBoolean renewalObserved = new AtomicBoolean(false);

        // Observing delivery port: at deliver time the row is still DISPATCHING
        // (markAcked runs after deliver) but its lease_until has already been
        // renewed by renewLease (committed, auto-commit) — so reading lease_until
        // == t0+90s here is the smoking gun that renewal fired before delivery.
        ForwardingDeliveryPort observingDelivery = (record, now) -> {
            Map<String, Object> row = outboxFullRow(tenant, messageId);
            assertThat(row.get("lease_until"))
                    .as("at deliver time the lease was already renewed to t0+90s "
                        + "(claim lease t0+30s + 60s extension) — proof renewLease ran")
                    .isEqualTo(expectedRenewedLeaseUntil);
            assertThat(row.get("lease_owner"))
                    .as("the renewed lease is still held by this worker")
                    .isEqualTo(leaseOwner);
            renewalObserved.set(true);
            return ForwardingDeliveryResult.acked();
        };

        // renewBeforeExpiryMillis=50s → remaining (30s) < 50s fires renewal;
        // leaseExtensionMillis=60s → extendedUntil = (t0+30s) + 60s = t0+90s.
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, observingDelivery,
                new ForwardingDispatcherWorker.DispatchLeasePolicy(50_000L, 60_000L),
                clock, ForwardingRetryPolicy.DEFAULT);

        // 1 tick at t0; loop.runOnce gets leaseUntil = t0 + claimLeaseDuration (t0+30s).
        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                worker, advanceableTickSource(clock, t0, 1_000, 1),
                ForwardingDispatchLoop.NO_BACKOFF);
        ForwardingDispatcherWorker.DispatchTickResult tick =
                loop.run(tenant, 5, leaseOwner, claimLeaseDurationMillis);

        assertThat(tick.acked())
                .as("the renewed lease let the record deliver and ack")
                .isEqualTo(1);
        assertThat(tick.skipped())
                .as("renewLease returned true → the record was not skipped")
                .isZero();
        assertThat(tick.retried()).isZero();
        assertThat(tick.dlqd()).isZero();
        assertThat(tick.expired()).isZero();
        assertThat(tick.claimed())
                .as("tick counts stay self-consistent")
                .isEqualTo(tick.acked() + tick.retried() + tick.dlqd() + tick.expired() + tick.skipped());
        assertThat(renewalObserved)
                .as("the observing port saw the renewed lease at deliver time — renewal fired")
                .isTrue();

        assertThat(outbox.statusOf(id(messageId), tenant))
                .as("the renewed-then-delivered record round-tripped to a persisted ACK")
                .isEqualTo(ForwardingStatus.Outbox.ACKED);
        Map<String, Object> row = outboxFullRow(tenant, messageId);
        assertThat(row.get("attempt_count"))
                .as("a clean ACK consumes no retry attempt")
                .isEqualTo(0);
    }

    // ---- time-control infrastructure (test-only, plain JDK) ----------------

    /**
     * Mutable, injectable wall clock (mirrors Stage 19/20). Lets the worker's
     * renewal check / delivery instant read a controllable instant instead of
     * {@link EpochClock#SYSTEM}. Coordinated with {@link #advanceableTickSource}
     * so the loop's tick instant and the worker's {@code clockNow} never diverge.
     */
    private static final class MutableEpochClock implements EpochClock {
        private long current;

        MutableEpochClock(long initialMillisEpoch) {
            this.current = initialMillisEpoch;
        }

        @Override
        public long epochMillis() {
            return current;
        }

        /** Advance the clock to exactly {@code instantMillisEpoch} (monotonic: must not go back). */
        void advanceTo(long instantMillisEpoch) {
            this.current = instantMillisEpoch;
        }
    }

    /**
     * Tick source that yields {@code ticks} instants starting at {@code baseInstant},
     * each {@code stepMillis} apart, then stops. Before yielding each instant it
     * advances the shared clock to the same value &mdash; the two-clock
     * coordination point.
     */
    private static ForwardingDispatchLoop.TickSource advanceableTickSource(
            MutableEpochClock clock, long baseInstant, long stepMillis, int ticks) {
        long[] emitted = {0};
        return () -> {
            if (emitted[0] >= ticks) {
                return OptionalLong.empty();
            }
            long instant = baseInstant + emitted[0] * stepMillis;
            emitted[0]++;
            clock.advanceTo(instant);
            return OptionalLong.of(instant);
        };
    }

    /**
     * Fake {@link ForwardingDeliveryPort} that replays a fixed outcome sequence.
     * The claim/renew/ack SQL against the real outbox is untouched; only the
     * delivery outcome is injected.
     */
    private static ForwardingDeliveryPort fakeDeliveryPort(List<ForwardingDeliveryResult> sequence) {
        java.util.Iterator<ForwardingDeliveryResult> results = sequence.iterator();
        return (record, now) -> results.next();
    }

    // ---- persistence / envelope helpers (mirror Stage 18/19/20 IT) ---------

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
     * Raw projection of the persisted outbox row (mirrors Stage 18/19/20 IT, here
     * extended with {@code status} / {@code lease_owner} / {@code lease_until} for
     * the EXPIRED-lease-released and renewal-lease-extended assertions). The port
     * exposes no per-record read path ({@code claimDue} leases), so the IT reads
     * the row directly to assert on-disk state.
     */
    private static Map<String, Object> outboxFullRow(String tenantId, String messageIdValue) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status, last_failure_code, attempt_count, next_attempt_at, "
                   + "lease_owner, lease_until "
                   + "FROM agent_bus_forwarding_outbox WHERE tenant_id = ? AND message_id = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, messageIdValue);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("outbox row exists for " + messageIdValue).isTrue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", rs.getString("status"));
                row.put("last_failure_code", rs.getString("last_failure_code"));
                row.put("attempt_count", rs.getInt("attempt_count"));
                row.put("next_attempt_at", rs.getObject("next_attempt_at"));
                row.put("lease_owner", rs.getString("lease_owner"));
                row.put("lease_until", rs.getObject("lease_until"));
                return row;
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to read outbox row " + messageIdValue, e);
        }
    }
}
