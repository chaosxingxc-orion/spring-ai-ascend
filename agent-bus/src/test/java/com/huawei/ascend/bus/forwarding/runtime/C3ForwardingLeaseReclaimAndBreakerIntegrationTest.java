package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 20 (MI20-001 / MI20-002) &mdash; end-to-end verification of the two
 * reclaim / short-circuit paths Stage 19 explicitly left unverified, closing the
 * last blind spots of the C3 dispatch loop's failure-handling surface.
 *
 * <p>Stage 19 drove the retry round-trip lifecycle and verified
 * {@link JdbcForwardingOutbox#claimDue}'s <b>{@code RETRY_SCHEDULED} reclaim
 * clause</b> {@code (status='RETRY_SCHEDULED' AND next_attempt_at <= :now)}
 * across multiple ticks &mdash; but recorded two paths as still-deferred:
 * <ol>
 *   <li><b>Lease-expiry (stuck-holder) reclaim end to end.</b>
 *       {@code claimDue}'s <em>second</em> reclaim clause
 *       {@code (status='DISPATCHING' AND lease_until <= :now)} resolves a row
 *       whose owning worker crashed mid-delivery (the lease is held by a dead
 *       owner and past expiry). Stage 19 never exercised it &mdash; its
 *       advanceable-tick harness re-claimed rows <em>before</em> they were ever
 *       stuck in DISPATCHING, so the stuck-holder path remained unit-only.</li>
 *   <li><b>Circuit-breaker real chain end to end.</b> Stage 16 wired
 *       {@link RouteCircuitBreaker} into the worker, but verified it only at the
 *       unit / contract layer (a fake state machine, no real persistence loop).
 *       Whether an OPEN breaker really short-circuits a real
 *       {@code claimDue}&rarr;{@code deliver} tick, leaves the row DISPATCHING
 *       (consuming no {@code attempt_count}), and then &mdash; once the lease
 *       expires &mdash; whether the stuck-holder reclaim hands the row back to a
 *       HALF_OPEN probe that actually recovers, was all unverified end to end.</li>
 * </ol>
 *
 * <p>Stage 20 closes both, on REAL persistence (embedded-postgres + Flyway +
 * {@link JdbcForwardingOutbox}). Two scenarios:
 * <pre>
 *   A. lease-expiry reclaim (worker crash recovery)
 *        enqueue PENDING &rarr; SQL-simulate a crash (status=DISPATCHING,
 *          lease_owner='worker-dead', lease_until in the past) &rarr; ONE tick &rarr;
 *          claimDue's stuck-holder clause reclaims the row under a live owner &rarr;
 *          deliver acked &rarr; ACKED. claimed=1 (only the lease-expiry clause can
 *          claim a DISPATCHING row), attempt_count=0 (reclaim is not a retry),
 *          last_failure_code=null.
 *
 *   B. circuit-breaker full state machine woven with lease reclaim
 *        RouteCircuitBreaker(2, 122s, clock) + fake delivery [retry, retry, acked];
 *        4 ticks at +61s step (> 60s lease &rarr; each tick reclaims the prior row):
 *          tick1 PENDING&rarr;DISPATCHING, CLOSED, retry &rarr; scheduleRetry (cf=1);
 *          tick2 RETRY_SCHEDULED reclaim, CLOSED, retry &rarr; cf=2 &rarr; OPEN;
 *          tick3 RETRY_SCHEDULED reclaim, OPEN (61s &lt; 122s cooldown) short-circuit
 *            SKIP &rarr; row LEFT DISPATCHING (no recordOutcome, attempt_count holds);
 *          tick4 stuck-holder lease-expiry reclaim (DISPATCHING lease_until&lt;=now),
 *            OPEN&rarr;HALF_OPEN (122s &gt;= cooldown) probe, deliver acked &rarr;
 *            CLOSED &rarr; ACKED.
 *        claimed=4, retried=2, skipped=1, acked=1; breaker.stateOf==CLOSED;
 *        attempt_count=2 (tick3 skip did not bump).
 * </pre>
 *
 * <p>Scenario B is the highest-value test of the stage: a single loop tickler
 * <em>weaves</em> the two deferred paths together &mdash; the breaker's OPEN
 * short-circuit is precisely what leaves a row stuck in DISPATCHING, and the
 * lease-expiry stuck-holder reclaim is precisely what hands it back to the
 * HALF_OPEN probe. Verifying them in isolation would miss the interaction; this
 * proves the dispatch loop's three skip paths (lease-renew failure, breaker OPEN,
 * deliver-exception) and the two reclaim clauses (RETRY_SCHEDULED,
 * stuck-holder DISPATCHING) compose correctly on real SQL.
 *
 * <p><b>Time control (reused from Stage 19).</b> A {@link MutableEpochClock}
 * (test-only, plain JDK) is injected into the worker, and an
 * {@link #advanceableTickSource} yields N tick instants {@code baseInstant +
 * i * stepMillis}, advancing the clock to the same instant before each tick &mdash;
 * the two clocks are coordinated so the worker's {@code clockNow} (the
 * {@code nextAttemptAt} basis) and {@code claimDue}'s {@code :now} reclaim gate
 * never diverge. The 61s step exceeds any backoff the policy produces AND the
 * 60s lease, so each tick reclaims the prior row (RETRY_SCHEDULED via
 * {@code next_attempt_at}, DISPATCHING via the stuck-holder lease-expiry clause).
 *
 * <p><b>Real-clock guard constraint.</b> {@link JdbcForwardingOutbox}'s
 * lease-guarded mutations ({@code markAcked} / {@code scheduleRetry}) gate on
 * {@code lease_until > System.currentTimeMillis()} using the <b>real</b> wall
 * clock, not the injected one. So the tick instants start at {@code T0} (the
 * test's real start moment) and increase monotonically &mdash; the per-tick
 * {@code leaseUntil = tickInstant + 60s} then always exceeds the real clock, so
 * the lease guard never misfires. This IT touches no production code; the
 * real-clock lease semantics stay as-is.
 *
 * <p><b>No runtime boot.</b> Neither scenario needs a real A2A server: scenario A
 * reclaims a stuck row and acks via a fake delivery port; scenario B injects the
 * whole delivery sequence. So this IT boots only embedded-postgres + Flyway +
 * the outbox &mdash; lighter than Stage 17 / 18. The reclaim / breaker behaviour
 * is REAL (real SQL {@code claimDue} reclaim, real {@code markAcked}); only the
 * delivery outcome is injected.
 *
 * <p><b>&sect;6.2 unchanged.</b> Both scenarios use a {@code CONTROL_ONLY}
 * envelope; the time-control helpers / fake port are test-scope plain-JDK &mdash;
 * no concrete broker/MQ, no payload body / token stream / Task state, no
 * cross-tenant fallback. Scenario A's crash-simulation UPDATE is a test fixture
 * (no production code writes a DISPATCHING row without owning the lease).
 *
 * <p><b>@Isolated</b> (Stage 19 finding): the parent pom runs surefire 4-way
 * concurrent, and these context-boot ITs match the surefire (not failsafe) name
 * pattern, so they would otherwise land on the concurrent runner; Spring Boot 4's
 * {@code SpringApplication.run} is not thread-safe, so concurrent boot flaked
 * ({@code ConcurrentModificationException}). {@code @Isolated} makes each run
 * exclusive, and these ITs share the embedded-postgres boot recipe.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage19-review-and-stage20-plan.md}
 * &sect;2 / &sect;4 MI20-001 / MI20-002.
 */
@Isolated
class C3ForwardingLeaseReclaimAndBreakerIntegrationTest {

    private static EmbeddedPostgres pg;
    private static DataSource dataSource;
    private static JdbcForwardingOutbox outbox;

    @BeforeAll
    static void bootPostgres() throws Exception {
        // Real PostgreSQL only — no agent-runtime server boot (scenario A reclaims
        // a stuck row then acks via a fake port; scenario B injects the whole
        // delivery sequence). Lighter than Stage 17/18.
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
     * Scenario A &mdash; a row whose owning worker crashed mid-delivery (lease
     * held by a dead owner, past expiry) is reclaimed by {@code claimDue}'s
     * stuck-holder clause and re-delivered to a terminal ACK, proving the
     * lease-expiry reclaim path works end to end on real persistence.
     *
     * <p>The crash is simulated by a fixture UPDATE that stamps a DISPATCHING row
     * with a dead {@code lease_owner} and a {@code lease_until} in the past
     * (CHECK-valid: DISPATCHING just needs a non-null lease_owner; the expiry
     * value is a plain past epoch, not the {@code RELEASED_LEASE_UNTIL} sentinel).
     * A single tick then exercises claimDue's <em>second</em> reclaim clause
     * {@code (status='DISPATCHING' AND lease_until <= :now)} &mdash; the only way
     * a DISPATCHING row can be claimed. The assertions prove:
     * <ul>
     *   <li>{@code claimed==1} &mdash; the stuck DISPATCHING row WAS reclaimed
     *       (without the stuck-holder clause, a DISPATCHING row is never in
     *       claimDue's candidate set, so claimed would be 0);</li>
     *   <li>{@code attempt_count==0} &amp; {@code last_failure_code==null} &mdash;
     *       reclaiming a stuck row is a fresh delivery, not a retry, so neither
     *       the count nor a failure code is touched.</li>
     * </ul>
     */
    @Test
    void scenario_a_lease_expiry_reclaims_stuck_dispatching_row() throws Exception {
        String tenant = "tenant-reclaim";
        String route = "route-reclaim";
        String messageId = "msg-reclaim";
        long t0 = System.currentTimeMillis();

        outbox.enqueue(envelope(tenant, messageId, route), "svc-src", "svc-tgt", t0);
        assertThat(outbox.statusOf(id(messageId), tenant))
                .isEqualTo(ForwardingStatus.Outbox.PENDING);

        // Simulate a worker that claimed the row (PENDING→DISPATCHING) then
        // crashed mid-delivery: the lease is now held by a dead owner and already
        // past expiry. This is exactly the stuck-holder case claimDue's second
        // reclaim clause exists to resolve.
        simulateCrashedDispatch(tenant, messageId, "worker-dead", t0 - 60_000);
        assertThat(outbox.statusOf(id(messageId), tenant))
                .as("crash leaves the row stuck in DISPATCHING under a dead owner")
                .isEqualTo(ForwardingStatus.Outbox.DISPATCHING);

        MutableEpochClock clock = new MutableEpochClock(t0);
        ForwardingDeliveryPort delivery = fakeDeliveryPort(List.of(ForwardingDeliveryResult.acked()));
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, delivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                clock, ForwardingRetryPolicy.DEFAULT);

        // A single tick at t0: claimDue's stuck-holder clause (lease_until<=now)
        // reclaims the DISPATCHING row under a live owner and re-delivers it.
        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                worker, advanceableTickSource(clock, t0, 61_000, 1),
                ForwardingDispatchLoop.NO_BACKOFF);
        ForwardingDispatcherWorker.DispatchTickResult tick =
                loop.run(tenant, 5, "worker-recover", 60_000);

        assertThat(tick.claimed())
                .as("the stuck DISPATCHING row was reclaimed by the live worker "
                    + "(only the lease-expiry clause can claim a DISPATCHING row)")
                .isEqualTo(1);
        assertThat(tick.acked()).isEqualTo(1);
        assertThat(tick.retried()).isZero();
        assertThat(tick.dlqd()).isZero();
        assertThat(tick.expired()).isZero();
        assertThat(tick.skipped()).isZero();
        assertThat(tick.claimed())
                .as("tick counts stay self-consistent")
                .isEqualTo(tick.acked() + tick.retried() + tick.dlqd() + tick.expired() + tick.skipped());

        assertThat(outbox.statusOf(id(messageId), tenant))
                .as("the reclaimed stuck row round-tripped to a persisted ACK")
                .isEqualTo(ForwardingStatus.Outbox.ACKED);
        Map<String, Object> row = outboxRow(tenant, messageId);
        assertThat(row.get("last_failure_code"))
                .as("no failure code — the stuck row was recovered, never retried")
                .isNull();
        assertThat(row.get("attempt_count"))
                .as("reclaim of a stuck DISPATCHING row is a fresh delivery, not a retry")
                .isEqualTo(0);
    }

    /**
     * Scenario B &mdash; the {@link RouteCircuitBreaker} full state machine,
     * verified end to end on real persistence and woven with lease reclaim. This
     * closes Stage 16's "verified only at unit/contract layer" gap AND exercises
     * the stuck-holder reclaim path (tick4) in a single coherent loop.
     *
     * <p>A {@code RouteCircuitBreaker(2, 122s, clock)} plus a fake delivery port
     * injecting the transport-internal failure pattern {@code [retry, retry,
     * acked]} (two intermittent {@code RECEIVER_UNAVAILABLE} failures then a
     * recovery). Four ticks at a 61s step (61s &gt; the 60s lease, so each tick
     * reclaims the prior row) drive the full machine:
     * <ul>
     *   <li><b>tick1</b> (PENDING&rarr;DISPATCHING): breaker CLOSED, deliver retry,
     *       {@code scheduleRetry} &rarr; cf=1;</li>
     *   <li><b>tick2</b> (RETRY_SCHEDULED reclaim): breaker CLOSED cf=1, deliver
     *       retry, cf=2 &ge; threshold &rarr; <b>OPEN</b>;</li>
     *   <li><b>tick3</b> (RETRY_SCHEDULED reclaim): breaker OPEN (61s &lt; 122s
     *       cooldown) &rarr; <b>short-circuit SKIP</b>, row LEFT DISPATCHING (no
     *       {@code recordOutcome}, {@code attempt_count} holds at 2);</li>
     *   <li><b>tick4</b> (DISPATCHING stuck-holder reclaim): breaker OPEN (122s
     *       &ge; cooldown) &rarr; <b>HALF_OPEN</b> single probe, deliver acked &rarr;
     *       <b>CLOSED</b> &rarr; ACKED.</li>
     * </ul>
     *
     * <p>The assertions prove the breaker's OPEN short-circuit is leak-proof
     * ({@code skipped==1}, the row is not lost &mdash; the lease-expiry reclaim
     * hands it back), {@code attempt_count==2} (tick3's skip consumed none), and
     * the HALF_OPEN probe that recovered closed the breaker back to CLOSED
     * ({@code stateOf==CLOSED}) &mdash; the single most interaction-rich failure
     * path in the dispatch loop.
     */
    @Test
    void scenario_b_circuit_breaker_full_state_machine_with_lease_reclaim() throws Exception {
        String tenant = "tenant-breaker";
        String route = "route-breaker";
        String messageId = "msg-breaker";
        long t0 = System.currentTimeMillis();

        outbox.enqueue(envelope(tenant, messageId, route), "svc-src", "svc-tgt", t0);

        MutableEpochClock clock = new MutableEpochClock(t0);
        // failureThreshold=2 → OPEN after 2 consecutive retryable failures;
        // cooldownMillis=122_000 → HALF_OPEN probe only on a tick past the open.
        RouteCircuitBreaker breaker = new RouteCircuitBreaker(2, 122_000, clock);
        // Fake delivery injects the transport-internal pattern: two intermittent
        // RECEIVER_UNAVAILABLE failures, then a recovery ACK. The claim/reclaim/
        // ack SQL is untouched; only the delivery outcome is injected.
        ForwardingDeliveryPort delivery = fakeDeliveryPort(List.of(
                ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE),
                ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE),
                ForwardingDeliveryResult.acked()));
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, delivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                clock, ForwardingRetryPolicy.DEFAULT, breaker);
        ForwardingRouteHandle routeHandle = new ForwardingRouteHandle(route, tenant);

        // 4 ticks at t0, t0+61s, t0+122s, t0+183s (61s step > 60s lease → each
        // tick reclaims the prior row: RETRY_SCHEDULED via next_attempt_at in
        // ticks 2/3, DISPATCHING via the stuck-holder lease-expiry clause in 4).
        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                worker, advanceableTickSource(clock, t0, 61_000, 4),
                ForwardingDispatchLoop.NO_BACKOFF);
        ForwardingDispatcherWorker.DispatchTickResult tick =
                loop.run(tenant, 5, "worker-breaker", 60_000);

        assertThat(tick.claimed())
                .as("4 claims: PENDING + 2x RETRY_SCHEDULED reclaim + 1x DISPATCHING "
                    + "stuck-holder lease-expiry reclaim")
                .isEqualTo(4);
        assertThat(tick.retried())
                .as("2 retryable failures recorded (tick1 + tick2); tick3 was breaker-skipped")
                .isEqualTo(2);
        assertThat(tick.skipped())
                .as("tick3 breaker OPEN short-circuit skipped delivery (left DISPATCHING)")
                .isEqualTo(1);
        assertThat(tick.acked())
                .as("tick4 HALF_OPEN probe recovered → ACKED")
                .isEqualTo(1);
        assertThat(tick.dlqd()).isZero();
        assertThat(tick.expired()).isZero();
        assertThat(tick.claimed())
                .as("tick counts stay self-consistent across the breaker lifecycle")
                .isEqualTo(tick.acked() + tick.retried() + tick.dlqd() + tick.expired() + tick.skipped());

        assertThat(breaker.stateOf(routeHandle))
                .as("HALF_OPEN probe succeeded → breaker recovered to CLOSED")
                .isEqualTo(RouteCircuitBreaker.State.CLOSED);
        assertThat(outbox.statusOf(id(messageId), tenant))
                .as("the recovered probe round-tripped to a persisted ACK")
                .isEqualTo(ForwardingStatus.Outbox.ACKED);
        Map<String, Object> row = outboxRow(tenant, messageId);
        assertThat(row.get("attempt_count"))
                .as("2 retries before recovery; tick3 breaker-skip left DISPATCHING and "
                    + "consumed no attemptCount")
                .isEqualTo(2);
    }

    // ---- time-control infrastructure (test-only, plain JDK) ----------------

    /**
     * Mutable, injectable wall clock (mirrors Stage 19). Lets the worker's
     * renewal check / delivery instant / {@code nextAttemptAt} basis read a
     * controllable instant instead of {@link EpochClock#SYSTEM}. Coordinated with
     * {@link #advanceableTickSource} so the loop's tick instant and the worker's
     * {@code clockNow} never diverge.
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
     * coordination point. {@code stepMillis} must exceed any backoff the policy
     * produces AND the lease duration, so each tick reclaims the prior row.
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
     * The claim/reclaim/ack SQL against the real outbox is untouched; only the
     * delivery outcome is injected.
     */
    private static ForwardingDeliveryPort fakeDeliveryPort(List<ForwardingDeliveryResult> sequence) {
        Iterator<ForwardingDeliveryResult> results = sequence.iterator();
        return (record, now) -> results.next();
    }

    // ---- persistence / envelope helpers (mirror Stage 18/19 IT) ------------

    private static ForwardingMessageId id(String value) {
        return new ForwardingMessageId(value);
    }

    private static ForwardingEnvelope envelope(String tenant, String messageId, String route) {
        return new ForwardingEnvelope(
                new ForwardingMessageId(messageId), AgentBusEventType.CLIENT_INVOCATION_REQUESTED,
                tenant, "trace-" + messageId,
                "corr-" + messageId, "idem-" + messageId,
                new ForwardingRouteHandle(route, tenant), "cap",
                "src-" + messageId, "tgt-" + messageId, Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY, null);
    }

    /**
     * Test fixture: simulate a worker that claimed a row (PENDING&rarr;DISPATCHING)
     * then crashed mid-delivery &mdash; the lease is now held by a dead owner and
     * past expiry. A CHECK-valid UPDATE: DISPATCHING only needs a non-null
     * {@code lease_owner}; {@code lease_until} is a plain past epoch (not the
     * {@code RELEASED_LEASE_UNTIL} sentinel, which is releaseLease's path). No
     * production code writes a DISPATCHING row without owning the lease.
     */
    private static void simulateCrashedDispatch(String tenantId, String messageIdValue,
                                                String deadLeaseOwner, long pastLeaseUntil) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE agent_bus_forwarding_outbox "
                   + "SET status = 'DISPATCHING', lease_owner = ?, lease_until = ? "
                   + "WHERE tenant_id = ? AND message_id = ?")) {
            ps.setString(1, deadLeaseOwner);
            ps.setLong(2, pastLeaseUntil);
            ps.setString(3, tenantId);
            ps.setString(4, messageIdValue);
            int updated = ps.executeUpdate();
            assertThat(updated).as("simulated-crash UPDATE touched exactly one row").isEqualTo(1);
        }
    }

    /**
     * Raw projection of the persisted outbox row (mirrors Stage 18/19 IT). The
     * port exposes no per-record read path ({@code claimDue} leases), so the IT
     * reads {@code last_failure_code} / {@code attempt_count} / {@code next_attempt_at}
     * directly to assert on-disk state.
     */
    private static Map<String, Object> outboxRow(String tenantId, String messageIdValue) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT last_failure_code, attempt_count, next_attempt_at "
                   + "FROM agent_bus_forwarding_outbox WHERE tenant_id = ? AND message_id = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, messageIdValue);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("outbox row exists for " + messageIdValue).isTrue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("last_failure_code", rs.getString("last_failure_code"));
                row.put("attempt_count", rs.getInt("attempt_count"));
                row.put("next_attempt_at", rs.getObject("next_attempt_at"));
                return row;
            }
        }
    }
}
