package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 21 (MI21-001 / MI21-002) &mdash; <b>multi-worker concurrency</b>
 * verification of the C3 dispatch loop on real persistence. A pure-test stage:
 * no production code changes. It closes the two Stage 20 deferrals that were
 * explicitly recorded as "verified only single-threaded":
 * <ol>
 *   <li><b>Concurrent claim under real contention.</b> Stage 12's
 *       {@code ForwardingJdbcIntegrationTest} proved {@code claimDue}'s
 *       {@code FOR UPDATE SKIP LOCKED} yields no duplicate <em>claims</em> in a
 *       2-thread unit test, and Stages 17&ndash;20 drove the full
 *       claim&rarr;deliver&rarr;ack chain &mdash; but only ever from a single
 *       worker instance per tick (Stage 20 scenario A simulated "another worker
 *       takes over" by reclaiming under a <em>different</em> lease owner on a
 *       later tick, never two workers racing the same instant). Whether N real
 *       workers, each running their own {@code runOnce} loop against the
 *       <em>same</em> shared outbox, really partition the work with zero
 *       duplicate deliveries and every record reaching its terminal state, was
 *       never exercised end to end.</li>
 *   <li><b>Shared circuit-breaker singleton under concurrency.</b> Stage 16
 *       wired {@link RouteCircuitBreaker} into the worker and verified the full
 *       state machine single-threaded (Stage 20 scenario B). The deferred note
 *       (Stage 16 risks) said: "a single {@code RouteCircuitBreaker} instance
 *       shared across worker <em>threads</em> is safe under
 *       {@code synchronized(RouteState)}; but the question of whether that
 *       actually holds under real concurrent {@code recordOutcome} traffic was
 *       left to production." This stage answers it.</li>
 * </ol>
 *
 * <p>Two scenarios, both on REAL persistence (embedded-postgres + Flyway +
 * {@link JdbcForwardingOutbox}), both <b>not</b> booting the runtime:
 *
 * <pre>
 *   A. concurrent claim &mdash; no duplicate deliveries
 *        enqueue M=20 PENDING records (same tenant/route, distinct message ids);
 *        N=4 worker threads, each its own {@code ForwardingDispatcherWorker} with a
 *        distinct lease owner, all sharing the SAME outbox + an atomic-counting
 *        delivery port (every {@code deliver} increments a counter and returns
 *        acked). A {@link CountDownLatch} releases all four simultaneously; each
 *        worker loops {@code runOnce} until {@code claimDue} returns nothing.
 *        Assert: the delivery counter == 20 (each record delivered EXACTLY once
 *        &mdash; if SKIP LOCKED failed and two workers claimed the same row, the
 *        loser's {@code markAcked} would trip the lease guard and skip, but the
 *        delivery port would already have been invoked a second time, so the
 *        counter would exceed 20); every record == ACKED; aggregate
 *        claimed/acked == 20.
 *
 *   B. shared breaker under concurrency &mdash; coherent OPEN
 *        enqueue M=12 PENDING records (same route); N=4 workers all sharing the
 *        SAME {@code RouteCircuitBreaker(2, very-large-cooldown, clock)} and a
 *        delivery port that always returns retry(RECEIVER_UNAVAILABLE). All four
 *        race {@code runOnce}. Assert: {@code breaker.stateOf(route) == OPEN}
 *        (consecutive failures crossed the threshold coherently &mdash; the
 *        {@code synchronized(RouteState)} block prevented a lost-update on
 *        {@code consecutiveFailures} or a torn state transition; and the OPEN
 *        state is <em>visible</em> to every worker via the
 *        {@code ConcurrentHashMap} memory-visibility guarantee); no worker threw
 *        (no {@code ConcurrentModificationException}, no illegal state); every
 *        per-tick {@code DispatchTickResult} stays self-consistent, so the
 *        aggregate does too; no record reached ACKED (the port never acks).
 * </pre>
 *
 * <p><b>Why scenario A's delivery counter is the smoking gun.</b> The lease
 * guard ({@code markAcked} rejects a record whose owner changed) is a
 * <em>second-line</em> defence: even if two workers both transitioned a row to
 * DISPATCHING, only one's ack would land and the other's would be skipped
 * &mdash; so the persisted end state could still look correct (one ACKED). The
 * delivery counter sits <em>before</em> the guard: it counts every
 * {@code deliver} invocation regardless of whether the subsequent ack succeeds.
 * A counter greater than M is direct proof of a duplicate claim that SKIP LOCKED
 * should have prevented. {@code == M} is direct proof it held.
 *
 * <p><b>Why scenario B's cooldown is very large.</b> The goal is to drive CLOSED
 * &rarr; OPEN under concurrent failures and verify the OPEN is coherent and
 * visible &mdash; <em>not</em> to re-verify the HALF_OPEN probe lifecycle (that
 * was Stage 20 scenario B, single-threaded, where time could be advanced
 * deterministically). Advancing the shared {@link MutableEpochClock} from
 * multiple threads while a HALF_OPEN probe is in flight would introduce a real
 * race between the cooldown check and {@code recordOutcome}; keeping the clock
 * frozen at {@code t0} with a cooldown far beyond it means the breaker can only
 * reach OPEN, never HALF_OPEN, so the only concurrent state writes are the
 * CLOSED&rarr;OPEN transition itself &mdash; exactly the lost-update hazard the
 * test targets.
 *
 * <p><b>Time control (reused from Stage 19/20).</b> A {@link MutableEpochClock}
 * frozen at {@code t0} (the test's real start) is injected into every worker.
 * The claim instant ({@code nowMillisEpoch}) and the delivery instant both read
 * {@code t0}; since delivery always returns a fixed outcome, the
 * {@code nextAttemptAt} basis is irrelevant. The injected {@code t0} also keeps
 * {@code RETRY_SCHEDULED} rows from being reclaimed in scenario B:
 * {@code nextAttemptAt = t0 + 100ms > t0 = now}, so {@code claimDue}'s
 * {@code RETRY_SCHEDULED} reclaim clause never fires and each worker's loop
 * terminates cleanly once the PENDING set is exhausted. No thread advances the
 * clock.
 *
 * <p><b>Real-clock guard constraint.</b> {@link JdbcForwardingOutbox}'s
 * lease-guarded mutations gate on {@code lease_until > System.currentTimeMillis()}
 * using the <b>real</b> wall clock. So the lease is set to {@code t0 + 120s}
 * (passed as {@code leaseUntilMillisEpoch}); the test finishes in well under
 * that, so the real clock never catches the lease and the guard never misfires
 * under contention. This IT touches no production code.
 *
 * <p><b>No runtime boot.</b> Neither scenario needs a real A2A server: both
 * inject the delivery outcome. So this IT boots only embedded-postgres + Flyway
 * + the outbox &mdash; lighter than Stage 17 / 18. The claim / lease / breaker
 * behaviour is REAL (real SQL {@code claimDue}, real {@code markAcked}, real
 * {@code RouteCircuitBreaker} state machine); only the delivery outcome is
 * injected.
 *
 * <p><b>&sect;6.2 unchanged.</b> Both scenarios use {@code CONTROL_ONLY}
 * envelopes; the time-control / counting helpers are test-scope plain-JDK &mdash;
 * no concrete broker/MQ, no payload body / token stream / Task state, no
 * cross-tenant fallback.
 *
 * <p><b>@Isolated</b> (Stage 19 finding, reused Stage 20): the parent pom runs
 * surefire 4-way concurrent, and these context-boot ITs match the surefire (not
 * failsafe) name pattern, so they would otherwise land on the concurrent runner;
 * Spring Boot 4's {@code SpringApplication.run} is not thread-safe. This IT does
 * not boot Spring, but it spawns its <em>own</em> worker threads and shares the
 * embedded-postgres boot recipe, so {@code @Isolated} keeps it exclusive.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage20-review-and-stage21-plan.md}
 * &sect;2 / &sect;4 MI21-001 / MI21-002.
 */
@Isolated
class C3ForwardingMultiWorkerConcurrencyIntegrationTest {

    private static EmbeddedPostgres pg;
    private static DataSource dataSource;
    private static JdbcForwardingOutbox outbox;

    @BeforeAll
    static void bootPostgres() throws Exception {
        // Real PostgreSQL only — no agent-runtime server boot (both scenarios
        // inject the delivery outcome). Lighter than Stage 17/18.
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
     * Scenario A &mdash; N workers racing {@code claimDue} against the same
     * shared outbox partition the M records with zero duplicate deliveries,
     * proving {@code FOR UPDATE SKIP LOCKED} holds under real end-to-end
     * contention (not just at the claim layer, as Stage 12's unit test did).
     *
     * <p>Four workers, each with a distinct lease owner, share one outbox and one
     * delivery port that counts every {@code deliver} call. A latch releases
     * them simultaneously; each loops {@code runOnce} until a tick claims
     * nothing. Because {@code deliver} is synchronous and the ack happens inside
     * the same {@code runOnce}, a worker's claimed rows are ACKED by the time
     * the call returns &mdash; so once every PENDING row is consumed, every
     * worker's next {@code claimDue} is empty and the loop exits. The
     * assertions prove:
     * <ul>
     *   <li>{@code deliveryCount == M} &mdash; each record delivered exactly
     *       once. This is the direct SKIP-LOCKED proof: a duplicate claim would
     *       invoke {@code deliver} twice for one row (the loser's ack is then
     *       rejected by the lease guard, but the delivery already happened),
     *       inflating the counter past M.</li>
     *   <li>every record == ACKED &mdash; the full claim&rarr;deliver&rarr;ack
     *       chain reached its terminal state for all M rows under contention;</li>
     *   <li>aggregate claimed / acked == M &mdash; no row was claimed but never
     *       resolved, and no row was double-counted.</li>
     * </ul>
     */
    @Test
    void scenario_a_concurrent_claim_skip_locked_no_duplicate_deliveries() throws Exception {
        String tenant = "tenant-concurrent-claim";
        String route = "route-concurrent-claim";
        int messages = 20;
        int workers = 4;
        long t0 = System.currentTimeMillis();
        long leaseUntil = t0 + 120_000; // 2 min: the real clock never catches it

        for (int i = 0; i < messages; i++) {
            outbox.enqueue(envelope(tenant, "msg-" + i, route), "svc-src", "svc-tgt", t0);
        }

        MutableEpochClock clock = new MutableEpochClock(t0);
        // Counting delivery port: every deliver() increments and returns acked.
        // The counter sits BEFORE the lease guard, so a duplicate claim (which
        // SKIP LOCKED must prevent) would inflate it past `messages`.
        AtomicLong deliveryCount = new AtomicLong();
        ForwardingDeliveryPort delivery = (record, now) -> {
            deliveryCount.incrementAndGet();
            return ForwardingDeliveryResult.acked();
        };

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<long[]>> futures = new ArrayList<>();

        for (int w = 0; w < workers; w++) {
            final String leaseOwner = "worker-claim-" + w;
            ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                    outbox, outbox, delivery,
                    ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                    clock, ForwardingRetryPolicy.DEFAULT,
                    ForwardingCircuitBreaker.ALWAYS_CLOSED);
            futures.add(pool.submit(() -> {
                startGate.await();
                long claimed = 0;
                long acked = 0;
                while (true) {
                    ForwardingDispatcherWorker.DispatchTickResult tick =
                            worker.runOnce(tenant, t0, 5, leaseOwner, leaseUntil);
                    claimed += tick.claimed();
                    acked += tick.acked();
                    if (tick.claimed() == 0) {
                        break;
                    }
                }
                return new long[]{claimed, acked};
            }));
        }

        startGate.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS))
                .as("all workers terminate within 60s (no infinite loop)")
                .isTrue();

        long totalClaimed = 0;
        long totalAcked = 0;
        for (Future<long[]> f : futures) {
            long[] r = f.get(); // runOnce swallows per-record failures; no exception expected
            totalClaimed += r[0];
            totalAcked += r[1];
        }

        assertThat(deliveryCount.get())
                .as("no duplicate deliveries under concurrent claim — SKIP LOCKED held "
                    + "(a dup claim would have delivered the same row twice, inflating "
                    + "the counter past %d)", messages)
                .isEqualTo(messages);
        assertThat(totalAcked)
                .as("every claimed record reached a terminal ACK")
                .isEqualTo(messages);
        assertThat(totalClaimed)
                .as("no record was double-claimed across workers")
                .isEqualTo(messages);

        for (int i = 0; i < messages; i++) {
            assertThat(outbox.statusOf(id("msg-" + i), tenant))
                    .as("msg-" + i + " reached terminal ACK under concurrent claim")
                    .isEqualTo(ForwardingStatus.Outbox.ACKED);
        }
    }

    /**
     * Scenario B &mdash; N workers sharing one {@link RouteCircuitBreaker}
     * singleton drive it coherently to OPEN under concurrent
     * {@code recordOutcome} traffic, proving the {@code synchronized(RouteState)}
     * block and the {@code ConcurrentHashMap} memory visibility hold in a real
     * multi-worker dispatch.
     *
     * <p>Four workers, distinct lease owners, share one outbox, one breaker
     * {@code (failureThreshold=2, very-large cooldown)}, and a delivery port
     * that always returns {@code retry(RECEIVER_UNAVAILABLE)}. All race
     * {@code runOnce}. The first two retryable failures cross the threshold and
     * trip CLOSED&rarr;OPEN; every subsequent {@code allowsDelivery} (from any
     * worker) sees OPEN and short-circuits (skip, left DISPATCHING, no
     * {@code attemptCount} consumed). The clock is frozen and the cooldown is
     * far beyond it, so the breaker can reach OPEN but never HALF_OPEN &mdash;
     * isolating the concurrent CLOSED&rarr;OPEN transition (the lost-update
     * hazard) from the HALF_OPEN probe lifecycle (already verified
     * single-threaded in Stage 20). The assertions prove:
     * <ul>
     *   <li>{@code breaker.stateOf(route) == OPEN} &mdash; the shared singleton
     *       reached a coherent OPEN visible to every worker (no lost update on
     *       {@code consecutiveFailures}, no torn state transition, and the
     *       {@code ConcurrentHashMap} published the OPEN to all threads);</li>
     *   <li>no worker threw &mdash; no {@code ConcurrentModificationException},
     *       no illegal state-machine transition under contention;</li>
     *   <li>aggregate {@code claimed == acked+retried+dlqd+expired+skipped}
     *       &mdash; every per-tick {@code DispatchTickResult} stayed
     *       self-consistent, so the aggregate across workers does too;</li>
     *   <li>no record reached ACKED &mdash; the port never acks, so every row
     *       is either RETRY_SCHEDULED (retry recorded before OPEN) or
     *       DISPATCHING (short-circuited after OPEN).</li>
     * </ul>
     */
    @Test
    void scenario_b_shared_breaker_singleton_concurrent_open() throws Exception {
        String tenant = "tenant-concurrent-breaker";
        String route = "route-concurrent-breaker";
        int messages = 12;
        int workers = 4;
        long t0 = System.currentTimeMillis();
        long leaseUntil = t0 + 120_000;

        for (int i = 0; i < messages; i++) {
            outbox.enqueue(envelope(tenant, "msgb-" + i, route), "svc-src", "svc-tgt", t0);
        }

        MutableEpochClock clock = new MutableEpochClock(t0);
        // failureThreshold=2 → OPEN after 2 consecutive retryable failures.
        // cooldown far beyond t0 → the breaker can reach OPEN but never
        // HALF_OPEN (clock frozen), isolating the concurrent CLOSED→OPEN
        // transition from the probe lifecycle already verified single-threaded.
        RouteCircuitBreaker breaker = new RouteCircuitBreaker(2, 3_600_000L, clock);
        // Delivery always fails retryably → drives the shared breaker to OPEN.
        ForwardingDeliveryPort delivery = (record, now) ->
                ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
        ForwardingRouteHandle routeHandle = new ForwardingRouteHandle(route, tenant);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<long[]>> futures = new ArrayList<>();

        for (int w = 0; w < workers; w++) {
            final String leaseOwner = "worker-breaker-" + w;
            ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                    outbox, outbox, delivery,
                    ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                    clock, ForwardingRetryPolicy.DEFAULT, breaker);
            futures.add(pool.submit(() -> {
                startGate.await();
                long claimed = 0;
                long acked = 0;
                long retried = 0;
                long dlqd = 0;
                long expired = 0;
                long skipped = 0;
                while (true) {
                    ForwardingDispatcherWorker.DispatchTickResult tick =
                            worker.runOnce(tenant, t0, 5, leaseOwner, leaseUntil);
                    claimed += tick.claimed();
                    acked += tick.acked();
                    retried += tick.retried();
                    dlqd += tick.dlqd();
                    expired += tick.expired();
                    skipped += tick.skipped();
                    if (tick.claimed() == 0) {
                        break;
                    }
                }
                return new long[]{claimed, acked, retried, dlqd, expired, skipped};
            }));
        }

        startGate.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS))
                .as("all workers terminate within 60s (OPEN short-circuit converges)")
                .isTrue();

        long totalClaimed = 0;
        long totalAcked = 0;
        long totalRetried = 0;
        long totalDlqd = 0;
        long totalExpired = 0;
        long totalSkipped = 0;
        for (Future<long[]> f : futures) {
            long[] r = f.get(); // no exception expected under contention
            totalClaimed += r[0];
            totalAcked += r[1];
            totalRetried += r[2];
            totalDlqd += r[3];
            totalExpired += r[4];
            totalSkipped += r[5];
        }

        assertThat(breaker.stateOf(routeHandle))
                .as("the shared breaker singleton reached a coherent OPEN visible to every "
                    + "worker — synchronized(RouteState) prevented a lost update on "
                    + "consecutiveFailures / a torn transition, and the ConcurrentHashMap "
                    + "published OPEN to all threads")
                .isEqualTo(RouteCircuitBreaker.State.OPEN);
        assertThat(totalRetried)
                .as("at least failureThreshold=2 retries were recorded before OPEN tripped")
                .isGreaterThanOrEqualTo(2);
        assertThat(totalAcked)
                .as("the delivery port never acks — no record can reach ACKED")
                .isZero();
        assertThat(totalClaimed)
                .as("aggregate tick counts stay self-consistent across workers")
                .isEqualTo(totalAcked + totalRetried + totalDlqd + totalExpired + totalSkipped);

        // Every record is either RETRY_SCHEDULED (retry recorded while CLOSED) or
        // DISPATCHING (short-circuited while OPEN) — none terminal-acked.
        Function<ForwardingStatus.Outbox, Boolean> terminal =
                s -> s == ForwardingStatus.Outbox.ACKED
                        || s == ForwardingStatus.Outbox.DLQ
                        || s == ForwardingStatus.Outbox.EXPIRED;
        for (int i = 0; i < messages; i++) {
            ForwardingStatus.Outbox s = outbox.statusOf(id("msgb-" + i), tenant);
            assertThat(terminal.apply(s))
                    .as("msgb-" + i + " is RETRY_SCHEDULED or DISPATCHING (retry before OPEN / "
                        + "short-circuit after OPEN), not a terminal ack; was " + s)
                    .isFalse();
        }
    }

    // ---- time-control infrastructure (test-only, plain JDK, mirror Stage 20) -

    /**
     * Mutable, injectable wall clock (mirrors Stage 19/20). Frozen at {@code t0}
     * here &mdash; no thread advances it (scenario B's HALF_OPEN probe lifecycle
     * is out of scope, already verified single-threaded in Stage 20). The frozen
     * instant keeps scenario B's {@code RETRY_SCHEDULED} rows from being
     * reclaimed ({@code nextAttemptAt = t0 + backoff > t0 = now}).
     */
    private static final class MutableEpochClock implements EpochClock {
        private final long current;

        MutableEpochClock(long initialMillisEpoch) {
            this.current = initialMillisEpoch;
        }

        @Override
        public long epochMillis() {
            return current;
        }
    }

    // ---- persistence / envelope helpers (mirror Stage 18/19/20 IT) ----------

    private static ForwardingMessageId id(String value) {
        return new ForwardingMessageId(value);
    }

    private static ForwardingEnvelope envelope(String tenant, String messageId, String route) {
        return new ForwardingEnvelope(
                new ForwardingMessageId(messageId), tenant, "trace-" + messageId,
                "corr-" + messageId, "idem-" + messageId,
                new ForwardingRouteHandle(route, tenant), "cap", Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY, null);
    }
}
