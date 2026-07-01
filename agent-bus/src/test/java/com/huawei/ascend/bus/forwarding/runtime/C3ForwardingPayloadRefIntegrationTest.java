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
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 23 (MI23-002) &mdash; end-to-end verification that a DATA_BEARING
 * envelope's {@code payloadRef} round-trips through REAL persistence unchanged,
 * closing the third high-value blind spot the prior stages left: Stages 17&ndash;22
 * ran every end-to-end verification with a CONTROL_ONLY envelope
 * ({@code payloadRef = null}), so the DATA_BEARING + payloadRef path &mdash; the
 * §6.2 data-reference mechanism ("large payloads travel as a reference, never in
 * the event / control channel") &mdash; was <em>never exercised end to end</em>.
 *
 * <p><b>Why this is the core blind spot.</b> The payload-reference invariant
 * (HD4 + ICD forwarding + L2 forwarding-persistence) says a runtime-to-runtime
 * message carries only a {@code payloadRef} (conditional, MI5-003 option B) when
 * it bears a payload, never the payload body. The plumbing is <em>complete</em>:
 * {@link ForwardingEnvelope} has the field, {@link ForwardingOutboxRecord} has it,
 * the DDL has {@code payload_ref VARCHAR(1024)}, and
 * {@link com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.ForwardingSqlCodec}
 * encodes/decodes it. But "complete plumbing" without an end-to-end round-trip is
 * exactly the shape of a latent bug &mdash; unlike Stage 22's EXPIRED (where the
 * <em>trigger source</em> was missing), here every layer is wired; it had simply
 * never been driven with a non-null {@code payloadRef} across real SQL.
 *
 * <p><b>Scope correction vs the Stage 22 plan.</b> The Stage 22 plan (this IT's
 * authority, &sect;2 / &sect;4) anticipated Stage 23 would "need to boot the
 * runtime + a handler that asserts 'received payloadRef metadata'." Pre-work
 * corrected this expectation: agent-bus is a <em>stateless reference-routing
 * layer</em> &mdash; its job is to <em>pass the payloadRef through</em>
 * (enqueue &rarr; claim &rarr; deliver &rarr; A2A metadata), not to handle the
 * payload the reference points to. The persistence checkpoint therefore needs
 * NO runtime boot: real PG + Flyway + {@link JdbcForwardingOutbox} + an observing
 * delivery port is enough. The companion transport checkpoint (Stage 23
 * MI23-003, {@code A2aForwardingDeliveryPortMockWebServerTest}) proves the
 * payloadRef reaches the A2A request metadata; whether the agent-runtime
 * receiver extracts it is <em>agent-runtime's</em> job, out of agent-bus's
 * boundary (deferred).
 *
 * <p><b>Scenario.</b> A single dispatch tick on a DATA_BEARING envelope drives
 * PENDING &rarr; DISPATCHING &rarr; ACKED, and an <em>observing</em> delivery
 * port asserts, <em>at deliver time</em>, that the payloadRef survived the full
 * round-trip on BOTH the record the worker handed it AND the persisted
 * {@code payload_ref} column. A final post-ACK read asserts the column survives
 * the terminal-ACK lifecycle (markAcked does not clear it). The assertions prove:
 * <ul>
 *   <li>at deliver time {@code record.payloadRef()} equals the enqueue value
 *       &mdash; {@code ForwardingSqlCodec} decoded the column correctly on
 *       claim;</li>
 *   <li>at deliver time the live PG {@code payload_ref} column equals the enqueue
 *       value &mdash; {@code enqueue} persisted it and {@code claim} did not
 *       mutate it;</li>
 *   <li>after ACKED the {@code payload_ref} column <em>still</em> equals the
 *       enqueue value &mdash; {@code markAcked} does not clear the reference
 *       (the data-reference path is append-only on the reference itself);</li>
 *   <li>the DATA_BEARING policy does not perturb the dispatch state machine:
 *       {@code claimed==1}, {@code acked==1}, the row is ACKED.</li>
 * </ul>
 *
 * <p><b>No runtime boot.</b> Real PostgreSQL only (embedded-postgres + Flyway +
 * {@link JdbcForwardingOutbox}); the delivery outcome is injected as
 * {@code acked()} by an observing port. No A2A server, no agent-runtime.
 *
 * <p><b>&sect;6.2 unchanged.</b> {@code payloadRef} is a String <em>reference</em>,
 * not a payload body / token stream / Task state / concrete broker client. The
 * observing port and raw-JDBC projection read are test-scope plain JDK.
 *
 * <p><b>@Isolated</b> (Stage 19 finding): the parent pom runs surefire 4-way
 * concurrent, and this context-boot IT matches the surefire (not failsafe) name
 * pattern; embedded-postgres boot is not safe under concurrent access, so the IT
 * runs exclusive of other tests in the suite.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage22-review-and-stage23-plan.md}
 * &sect;2.3 / &sect;4 MI23-002.
 */
@Isolated
class C3ForwardingPayloadRefIntegrationTest {

    private static EmbeddedPostgres pg;
    private static DataSource dataSource;
    private static JdbcForwardingOutbox outbox;

    @BeforeAll
    static void bootPostgres() throws Exception {
        // Real PostgreSQL only — no agent-runtime server boot (the observing port
        // injects acked()). Lighter than Stage 17/18.
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
     * The DATA_BEARING payloadRef round-trips through real persistence unchanged,
     * closing the Stage 17&ndash;22 blind spot where every end-to-end run used a
     * CONTROL_ONLY envelope. A single dispatch tick drives PENDING &rarr; ACKED,
     * and an observing delivery port proves the payloadRef survived the full
     * enqueue &rarr; claim &rarr; deliver path on BOTH the decoded record AND the
     * persisted column. A post-ACK read proves the column survives the terminal
     * lifecycle (markAcked does not clear the reference).
     *
     * <p>This is the strongest possible evidence that the data-reference plumbing
     * (envelope &rarr; record &rarr; DDL {@code payload_ref} &rarr; SqlCodec) is
     * not just present but <em>correct end to end</em> &mdash; because every layer
     * of the reference is exercised against real SQL with a non-null value, which
     * no prior Stage did.
     */
    @Test
    void data_bearing_payload_ref_round_trips_through_real_persistence() throws Exception {
        String tenant = "tenant-payload";
        String route = "route-payload";
        String messageId = "msg-payload";
        String payloadRef = "ref://tenant-payload/payload/msg-payload";
        long t0 = System.currentTimeMillis();

        outbox.enqueue(envelope(tenant, messageId, route, Long.MAX_VALUE, payloadRef),
                "svc-src", "svc-tgt", t0);
        assertThat(outbox.statusOf(id(messageId), tenant))
                .isEqualTo(ForwardingStatus.Outbox.PENDING);

        MutableEpochClock clock = new MutableEpochClock(t0);
        AtomicBoolean payloadRefObserved = new AtomicBoolean(false);

        // Observing delivery port: at deliver time the row is DISPATCHING but its
        // payload_ref was written at enqueue and left untouched by claim. Asserting
        // BOTH record.payloadRef() (SqlCodec decode) AND the live PG column (raw
        // JDBC) equal the enqueue value is the smoking gun that the reference
        // survived the full round-trip.
        ForwardingDeliveryPort observingDelivery = (record, now) -> {
            assertThat(record.payloadRef())
                    .as("the DATA_BEARING payloadRef survived enqueue → claim → deliver "
                        + "on the decoded record (SqlCodec round-trip)")
                    .isEqualTo(payloadRef);
            Map<String, Object> row = outboxFullRow(tenant, messageId);
            assertThat(row.get("payload_ref"))
                    .as("the payload_ref column persisted by SqlCodec encode and read "
                        + "back by raw JDBC matches at deliver time")
                    .isEqualTo(payloadRef);
            payloadRefObserved.set(true);
            return ForwardingDeliveryResult.acked();
        };

        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, observingDelivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                clock, ForwardingRetryPolicy.DEFAULT);

        // 1 tick at t0: claim the PENDING DATA_BEARING row, deliver acked()
        // (observing port asserts payloadRef) → markAcked → ACKED.
        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                worker, advanceableTickSource(clock, t0, 1_000, 1),
                ForwardingDispatchLoop.NO_BACKOFF);
        ForwardingDispatcherWorker.DispatchTickResult tick =
                loop.run(tenant, 5, "worker-payload", 60_000);

        assertThat(tick.acked())
                .as("the DATA_BEARING record delivered and acked")
                .isEqualTo(1);
        assertThat(tick.skipped()).isZero();
        assertThat(tick.retried()).isZero();
        assertThat(tick.dlqd()).isZero();
        assertThat(tick.expired()).isZero();
        assertThat(tick.claimed())
                .as("tick counts stay self-consistent")
                .isEqualTo(tick.acked() + tick.retried() + tick.dlqd() + tick.expired() + tick.skipped());
        assertThat(payloadRefObserved)
                .as("the observing port saw the payloadRef at deliver time")
                .isTrue();

        assertThat(outbox.statusOf(id(messageId), tenant))
                .as("the DATA_BEARING record round-tripped to a persisted ACK")
                .isEqualTo(ForwardingStatus.Outbox.ACKED);
        Map<String, Object> row = outboxFullRow(tenant, messageId);
        assertThat(row.get("payload_ref"))
                .as("the payload_ref column survives the terminal-ACK lifecycle "
                    + "(markAcked does not clear the data-reference)")
                .isEqualTo(payloadRef);
    }

    // ---- time-control infrastructure (test-only, plain JDK) ----------------

    /**
     * Mutable, injectable wall clock (mirrors Stage 19/20/22). Lets the worker's
     * delivery instant read a controllable value instead of
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

    // ---- persistence / envelope helpers (mirror Stage 22 IT) ---------------

    private static ForwardingMessageId id(String value) {
        return new ForwardingMessageId(value);
    }

    /**
     * Builds a DATA_BEARING envelope carrying {@code payloadRef} (non-null
     * &rarr; DATA_BEARING policy). This is the one-line delta vs the Stage 17&ndash;22
     * envelope helpers, which all ended in {@code CONTROL_ONLY, null}.
     */
    private static ForwardingEnvelope envelope(String tenant, String messageId, String route,
                                               long deadlineMillisEpoch, String payloadRef) {
        return new ForwardingEnvelope(
                new ForwardingMessageId(messageId), tenant, "trace-" + messageId,
                "corr-" + messageId, "idem-" + messageId,
                new ForwardingRouteHandle(route, tenant), "cap", deadlineMillisEpoch,
                payloadRef == null
                        ? ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY
                        : ForwardingEnvelope.PayloadPolicy.DATA_BEARING,
                payloadRef);
    }

    /**
     * Raw projection of the persisted outbox row (mirrors Stage 18/19/20/22 IT,
     * here extended with the {@code payload_ref} column for the Stage 23
     * data-reference round-trip assertions). The port exposes no per-record read
     * path ({@code claimDue} leases), so the IT reads the row directly to assert
     * on-disk state.
     */
    private static Map<String, Object> outboxFullRow(String tenantId, String messageIdValue) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status, last_failure_code, attempt_count, next_attempt_at, "
                   + "lease_owner, lease_until, payload_ref "
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
                row.put("payload_ref", rs.getString("payload_ref"));
                return row;
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to read outbox row " + messageIdValue, e);
        }
    }
}
