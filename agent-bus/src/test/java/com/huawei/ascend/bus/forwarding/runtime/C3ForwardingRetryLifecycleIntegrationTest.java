package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.MapEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.a2a.A2aForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.a2a.A2aForwardingProperties;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.ServerSocket;
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
 * Stage 19 (MI19-001 / MI19-002) &mdash; end-to-end verification of the C3 retry
 * round-trip lifecycle, closing Stage 18's largest blind spot.
 *
 * <p>Stage 18 pushed a record into {@code RETRY_SCHEDULED} on a real Postgres outbox
 * and stopped (one tick): it proved the <em>entry</em> to the retry path (route
 * unreachable &rarr; {@code retry(RECEIVER_UNAVAILABLE)} &rarr; {@code attempt_count=1}
 * + a future {@code next_attempt_at}). But {@link JdbcForwardingOutbox#claimDue}'s
 * {@code RETRY_SCHEDULED} reclaim clause {@code (status='RETRY_SCHEDULED' AND
 * next_attempt_at <= :now)} &mdash; the heart of the outbox value proposition
 * (persist + automatically re-drive to success or DLQ) &mdash; had <b>never been
 * triggered and verified end to end</b>. Whether the re-claim actually happens,
 * whether {@code attempt_count} really climbs across re-deliveries, whether the
 * {@link ForwardingRetryPolicy} {@code exhausted} gate fires at the right count, and
 * whether the two exits (exhausted&rarr;DLQ, recover&rarr;ACKED) actually close the
 * loop &mdash; all unverified on real persistence, covered only by the
 * fake-delivery unit / contract layer.
 *
 * <p>Stage 19 drives the same record <em>past</em> {@code next_attempt_at} across
 * multiple ticks on REAL persistence (embedded-postgres + Flyway +
 * {@link JdbcForwardingOutbox}), watching {@code claimDue} reclaim it, re-deliver it,
 * and walk {@code attempt_count} up to one of the two exits. Two scenarios:
 * <pre>
 *   A. constant failure &rarr; retry re-driven 3x &rarr; exhausted(maxAttempts) &rarr; DLQ
 *        unreachable route (real socket refusal) &rarr; retry(RECEIVER_UNAVAILABLE) on
 *          every attempt; a small maxAttempts=3 policy reaches exhaustion in 4 ticks;
 *          outbox ends DLQ, attempt_count=3 (moveToDlq does NOT bump attempt_count).
 *
 *   B. intermittent failure recovers &rarr; retry re-driven 2x &rarr; ACKED
 *        a fake delivery port injects [retry, retry, acked]; the 3rd attempt recovers;
 *          outbox ends ACKED, attempt_count=2 (markAcked does NOT bump attempt_count).
 * </pre>
 *
 * <p><b>Time control (the core addition).</b> Running the retry lifecycle for real
 * would mean sleeping through the backoff (base 100ms / cap 60s / maxAttempts 5) &mdash;
 * slow and flaky. Instead a {@link MutableEpochClock} (test-only, plain JDK) is
 * injected into the worker, and an {@link #advanceableTickSource} drives the loop,
 * yielding N tick instants {@code baseInstant + i * stepMillis} (<code>+61s</code> &gt;
 * any backoff so each tick is past the prior {@code next_attempt_at}). The two clocks
 * are <b>coordinated</b>: the tick source advances the {@link MutableEpochClock} to the
 * same instant before each tick, so the worker's {@code clockNow = clock.epochMillis()}
 * (the {@code nextAttemptAt} basis) and {@code claimDue}'s {@code :now} (the tick
 * instant) never diverge &mdash; the single most error-prone wiring point in this
 * chain (see the plan's &sect;3 finding #3).
 *
 * <p><b>Real-clock guard constraint.</b> {@link JdbcForwardingOutbox}'s
 * lease-guarded mutations ({@code markAcked} / {@code scheduleRetry} / {@code moveToDlq})
 * gate on {@code lease_until > System.currentTimeMillis()} using the <b>real</b> wall
 * clock, not the injected one. So the tick instant must start at {@code T0} (the test's
 * real start moment) and increase monotonically &mdash; the per-tick
 * {@code leaseUntil = tickInstant + 60s} then always exceeds the real clock, so the
 * lease guard never misfires as expired. This IT touches no production code; the
 * real-clock lease semantics stay as-is.
 *
 * <p><b>No runtime boot.</b> Neither scenario needs a real A2A server: scenario A's
 * delivery fails at a dead port before any server is involved; scenario B uses a fake
 * delivery port. So this IT boots only embedded-postgres + Flyway + the outbox &mdash;
 * lighter than Stage 17 / 18 (no {@code LocalA2aRuntimeHost}, no
 * {@code spring.autoconfigure.exclude}). The retry-lifecycle persistence behaviour is
 * REAL (real SQL {@code claimDue} reclaim, real {@code scheduleRetry} bumps); only
 * scenario B's <em>delivery outcome</em> is injected.
 *
 * <p><b>&sect;6.2 unchanged.</b> Both scenarios use a {@code CONTROL_ONLY} envelope;
 * {@link MutableEpochClock} / the tick source / the fake port are test-scope plain-JDK
 * helpers &mdash; no concrete broker/MQ, no payload body / token stream / Task state,
 * no cross-tenant fallback.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage18-review-and-stage19-plan.md}
 * &sect;2 / &sect;4 MI19-001 / MI19-002.
 */
class C3ForwardingRetryLifecycleIntegrationTest {

    private static EmbeddedPostgres pg;
    private static DataSource dataSource;
    private static JdbcForwardingOutbox outbox;

    @BeforeAll
    static void bootPostgres() throws Exception {
        // Real PostgreSQL only — no agent-runtime server boot (neither scenario needs
        // one: scenario A fails at a dead socket, scenario B uses a fake delivery port).
        // Lighter than Stage 17/18: no LocalA2aRuntimeHost, no spring.autoconfigure.exclude.
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
     * Scenario A &mdash; a record that fails on every delivery is re-driven by
     * {@code claimDue}'s {@code RETRY_SCHEDULED} reclaim clause across multiple ticks
     * until the retry policy's {@code exhausted} gate fires, then lands in DLQ.
     *
     * <p>The route points at an unlistened port, so the real transport gets a genuine
     * socket refusal on every attempt &rarr; {@code retry(RECEIVER_UNAVAILABLE)}.
     * A small {@code maxAttempts=3} policy reaches exhaustion in 4 ticks (attempt 0/1/2
     * retry, attempt 3 exhausted&rarr;DLQ). The assertions prove:
     * <ul>
     *   <li>{@code claimDue} really reclaimed the {@code RETRY_SCHEDULED} row 3 times
     *       ({@code retried==3} &amp; {@code attempt_count==3} &mdash; if reclaim never
     *       fired, {@code attempt_count} would be stuck at 1);</li>
     *   <li>{@code attempt_count} stops at {@code maxAttempts} because {@code moveToDlq}
     *       does not bump it (only {@code scheduleRetry} does).</li>
     * </ul>
     */
    @Test
    void dispatch_loop_retries_to_exhaustion_then_dlq() throws Exception {
        String tenant = "tenant-retry-life";
        String route = "route-retry-life";
        String messageId = "msg-retry-life";
        long t0 = System.currentTimeMillis();

        ForwardingReceipt receipt = outbox.enqueue(envelope(tenant, messageId, route),
                "svc-src", "svc-tgt", t0);
        assertThat(receipt.accepted()).isTrue();
        assertThat(outbox.statusOf(id(messageId), tenant)).isEqualTo(ForwardingStatus.Outbox.PENDING);

        int deadPort = freeUnusedPort();
        ForwardingEndpointResolver resolver = new MapEndpointResolver(
                Map.of(route, "http://localhost:" + deadPort + "/a2a"));
        A2aForwardingDeliveryPort delivery = new A2aForwardingDeliveryPort(resolver,
                new A2aForwardingProperties(2_000L, "X-Tenant-Id"));

        MutableEpochClock clock = new MutableEpochClock(t0);
        // maxAttempts=3 → the record is attempted once, retried up to 3 times, then
        // exhausted on the 4th claim. Backoff math is otherwise production-identical.
        ForwardingRetryPolicy smallPolicy =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 60_000L, 3, () -> 0L);
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, delivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                clock, smallPolicy);

        // 4 ticks at T0, T0+61s, T0+122s, T0+183s — each past the prior next_attempt_at
        // (= tickInstant + 100/200/400ms backoff), so claimDue's RETRY_SCHEDULED
        // (next_attempt_at <= :now) clause reclaims it each time.
        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                worker, advanceableTickSource(clock, t0, 61_000, 4),
                ForwardingDispatchLoop.NO_BACKOFF);

        ForwardingDispatcherWorker.DispatchTickResult tick =
                loop.run(tenant, 5, "worker-retry-life", 60_000);

        assertThat(tick.claimed())
                .as("same record claimed 4x: once PENDING + 3x RETRY_SCHEDULED reclaim")
                .isEqualTo(4);
        assertThat(tick.retried())
                .as("3 retryable failures re-driven by claimDue reclaim")
                .isEqualTo(3);
        assertThat(tick.dlqd())
                .as("4th attempt exhausted (attemptCount==maxAttempts) → DLQ")
                .isEqualTo(1);
        assertThat(tick.acked()).isZero();
        assertThat(tick.expired()).isZero();
        assertThat(tick.skipped()).isZero();
        // Self-consistency invariant (ForwardingDispatcherWorker.DispatchTickResult).
        assertThat(tick.claimed())
                .as("tick counts stay self-consistent across the retry lifecycle")
                .isEqualTo(tick.acked() + tick.retried() + tick.dlqd() + tick.expired() + tick.skipped());

        assertThat(outbox.statusOf(id(messageId), tenant))
                .as("the re-driven failure round-tripped to a persisted DLQ")
                .isEqualTo(ForwardingStatus.Outbox.DLQ);
        Map<String, Object> row = outboxRow(tenant, messageId);
        assertThat(row.get("last_failure_code"))
                .as("RECEIVER_UNAVAILABLE wire code persisted on the DLQ record")
                .isEqualTo(ForwardingFailureCode.RECEIVER_UNAVAILABLE.wireCode());
        assertThat(row.get("attempt_count"))
                .as("attempt_count climbed to maxAttempts across reclaim then stopped "
                    + "(moveToDlq does not bump; only scheduleRetry does)")
                .isEqualTo(3);
    }

    /**
     * Scenario B &mdash; a record that fails the first two deliveries but recovers on
     * the third is re-driven by {@code claimDue} reclaim to a terminal ACK, proving the
     * outbox re-drives to <em>success</em>, not just to DLQ.
     *
     * <p>A fake delivery port injects the outcome sequence {@code [retry, retry, acked]}
     * (the real <em>transport-internal</em> failure pattern: an intermittent socket
     * refusal / timeout recovers). Stage 18 mapped a remote agent's business
     * {@code FAILED} terminal to {@code dlq(REMOTE_TASK_FAILED)} &mdash; so a handler
     * business failure cannot model "retry then recover" (it would DLQ on attempt 1).
     * Retryable failure is a <em>transport-layer</em> concept; the fake port injects it
     * directly. The claimDue reclaim + attempt_count bump + markAcked are all REAL SQL.
     */
    @Test
    void dispatch_loop_recovers_to_acked_after_intermittent_failure() throws Exception {
        String tenant = "tenant-recover";
        String route = "route-recover";
        String messageId = "msg-recover";
        long t0 = System.currentTimeMillis();

        outbox.enqueue(envelope(tenant, messageId, route), "svc-src", "svc-tgt", t0);

        MutableEpochClock clock = new MutableEpochClock(t0);
        ForwardingDeliveryPort delivery = fakeDeliveryPort(List.of(
                ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE),
                ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE),
                ForwardingDeliveryResult.acked()));
        // DEFAULT policy (maxAttempts=5) — far from exhaustion, so recovery is what stops the loop.
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, delivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                clock, ForwardingRetryPolicy.DEFAULT);

        // 3 ticks: two re-deliveries fail and re-schedule, the third recovers and ACKs.
        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                worker, advanceableTickSource(clock, t0, 61_000, 3),
                ForwardingDispatchLoop.NO_BACKOFF);

        ForwardingDispatcherWorker.DispatchTickResult tick =
                loop.run(tenant, 5, "worker-recover", 60_000);

        assertThat(tick.claimed())
                .as("same record claimed 3x: once PENDING + 2x RETRY_SCHEDULED reclaim")
                .isEqualTo(3);
        assertThat(tick.retried())
                .as("2 intermittent failures re-driven by claimDue reclaim")
                .isEqualTo(2);
        assertThat(tick.acked())
                .as("3rd attempt recovered → terminal ACK")
                .isEqualTo(1);
        assertThat(tick.dlqd()).isZero();
        assertThat(tick.expired()).isZero();
        assertThat(tick.skipped()).isZero();
        assertThat(tick.claimed())
                .as("tick counts stay self-consistent across recovery")
                .isEqualTo(tick.acked() + tick.retried() + tick.dlqd() + tick.expired() + tick.skipped());

        assertThat(outbox.statusOf(id(messageId), tenant))
                .as("the recovered re-delivery round-tripped to a persisted ACK")
                .isEqualTo(ForwardingStatus.Outbox.ACKED);
        Map<String, Object> row = outboxRow(tenant, messageId);
        assertThat(row.get("attempt_count"))
                .as("2 retries recorded before recovery (markAcked does not bump)")
                .isEqualTo(2);
    }

    // ---- time-control infrastructure (test-only, plain JDK) ----------------

    /**
     * Mutable, injectable wall clock. Lets the worker's renewal check / delivery
     * instant / {@code nextAttemptAt} basis read a controllable instant instead of
     * {@link EpochClock#SYSTEM}. Coordinated with {@link #advanceableTickSource} so the
     * loop's tick instant and the worker's {@code clockNow} never diverge.
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
     * {@linkplain MutableEpochClock#advanceTo advances the shared clock} to the same
     * value &mdash; the two-clock coordination point. This keeps the worker's
     * {@code clockNow} (the {@code nextAttemptAt} basis) identical to the tick instant
     * that {@code claimDue} uses as its {@code :now} reclaim gate.
     *
     * <p>{@code stepMillis} must exceed any backoff the policy can produce (the default
     * {@code +61s} beats the {@code 60s} cap), so each tick is past the prior record's
     * {@code next_attempt_at} and {@code claimDue} reclaims it.
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
     * Fake {@link ForwardingDeliveryPort} that replays a fixed outcome sequence &mdash;
     * models an intermittent transport failure that recovers. The claim/reclaim/ack
     * SQL against the real outbox is untouched; only the delivery outcome is injected.
     */
    private static ForwardingDeliveryPort fakeDeliveryPort(List<ForwardingDeliveryResult> sequence) {
        Iterator<ForwardingDeliveryResult> results = sequence.iterator();
        return (record, now) -> results.next();
    }

    // ---- persistence / envelope helpers (mirror Stage 18 IT) ----------------

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

    /**
     * A port that is (transiently) not listened on: bind an ephemeral socket then close
     * it, so the real transport gets a genuine connection refusal (mirrors Stage 18
     * scenario 2 — no MockWebServer, a route whose target agent-runtime is down).
     */
    private static int freeUnusedPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /**
     * Raw projection of the persisted outbox row — the port exposes no per-record read
     * path ({@code claimDue} leases), so the IT reads {@code last_failure_code} /
     * {@code attempt_count} / {@code next_attempt_at} directly to assert on-disk retry
     * state across the lifecycle (mirrors Stage 18 IT).
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
