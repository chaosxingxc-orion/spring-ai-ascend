package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.MapEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.a2a.A2aForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.a2a.A2aForwardingProperties;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import com.huawei.ascend.runtime.app.LocalA2aRuntimeHost;
import com.huawei.ascend.runtime.app.RuntimeApp;
import com.huawei.ascend.runtime.app.RunningRuntime;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 18 (MI18-003) — end-to-end FAILURE paths across the agent-bus &harr;
 * agent-runtime boundary, complementing Stage 17's happy-path-only
 * {@link C3ForwardingEndToEndIntegrationTest}.
 *
 * <p>Stage 17 proved the chain drives a REAL A2A server to a terminal COMPLETED Task
 * and back to an outbox ACK — but only the happy path. Every failure-handling path
 * landed in Stages 7-16 (ACK / RETRY / DLQ / EXPIRED, lease-guarded mutation, retry
 * policy, circuit breaker) was until now covered only by fake-delivery unit / contract
 * tests, never on the real end-to-end chain. Stage 18 closes that gap, and the
 * {@code REMOTE_TASK_FAILED} non-retryable code (deferred three times: 14 &rarr; 15
 * &rarr; 17) lands alongside it so the FAILED terminal maps precisely rather than
 * being conservatively retried.
 *
 * <p>Two failure scenarios on REAL infrastructure (embedded-postgres + Flyway +
 * {@link JdbcForwardingOutbox} + a REAL {@link LocalA2aRuntimeHost}):
 * <pre>
 *   1. remote agent FAILED terminal &rarr; dlq(REMOTE_TASK_FAILED) &rarr; outbox DLQ
 *        FailingHandler (real server) issues AgentExecutionResult.failed(...)
 *          &rarr; A2aResultRouter task FAILED &rarr; real SSE FAILED frame
 *          &rarr; A2aForwardingDeliveryPort dlq(REMOTE_TASK_FAILED) [Stage 18 MI18-002]
 *          &rarr; worker moveToDlq &rarr; persisted last_failure_code = remote_task_failed
 *
 *   2. route unreachable &rarr; retry(RECEIVER_UNAVAILABLE) &rarr; outbox RETRY_SCHEDULED
 *        resolver &rarr; an unlistened port &rarr; real socket connection refusal
 *          &rarr; A2aForwardingDeliveryPort retry(RECEIVER_UNAVAILABLE)
 *          &rarr; worker scheduleRetry (Stage 14 policy) &rarr; persisted
 *            last_failure_code = receiver_unavailable, attempt_count = 1, next_attempt_at set
 * </pre>
 *
 * <p>Both scenarios reuse the Stage 17 boot recipe (embedded-postgres + Flyway +
 * {@code spring.autoconfigure.exclude} for Spring Boot 4's repackaged jdbc / flyway
 * autoconfigure — agent-runtime's server context is otherwise sensitive to the JDBC
 * starter agent-bus leaks onto the shared test classpath; see Stage 17 finding). The
 * only difference from Stage 17 is the handler: a {@link FailingHandler} whose
 * {@code resultAdapter} maps every raw result to
 * {@link AgentExecutionResult#failed(String, String)}, so the real server reaches a
 * terminal FAILED Task instead of COMPLETED.
 *
 * <p><b>Why scenario 2 needs no runtime.</b> The unreachable route points at a port
 * nothing listens on; {@code deliver} fails at the socket before any real server is
 * involved, so the {@link FailingHandler} runtime is simply not on that path (the boot
 * still happens for scenario 1, and reusing the same class keeps one @BeforeAll).
 *
 * <p><b>Reading persisted failure state.</b> The outbox port exposes no per-record
 * reader by design — {@code claimDue} leases and is not a read path — so the IT reads
 * the on-disk {@code last_failure_code} / {@code attempt_count} / {@code next_attempt_at}
 * columns via a raw JDBC projection, asserting them against the
 * {@link ForwardingFailureCode#wireCode()} contract.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage17-review-and-stage18-plan.md}
 * &sect;2.4 / &sect;4 MI18-003.
 */
// Stage 19 flaky-build fix: this IT boots a Spring Boot context (RuntimeApp.run). The
// parent pom's surefire runs test CLASSES concurrently (junitParallel=true, 4-way);
// Spring Boot 4 SpringApplication.run() is not thread-safe, so two context-booting ITs
// racing in one reused JVM hit ConcurrentModificationException and collide on the
// global spring.autoconfigure.exclude System property. @Isolated makes the JUnit
// platform run this class alone — no other class concurrently. (Ideally these would
// be *IT.java and run under failsafe, which is already serial; that rename is left to
// the IT's owners. @Isolated is the minimal localised fix.)
@Isolated
class C3ForwardingFailurePathIntegrationTest {

    private static EmbeddedPostgres pg;
    private static DataSource dataSource;
    private static JdbcForwardingOutbox outbox;
    private static RunningRuntime runtime;
    private static int port;

    @BeforeAll
    static void bootPostgresAndFailingRuntime() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        dataSource = pg.getPostgresDatabase();
        Flyway.configure().dataSource(dataSource).load().migrate();
        outbox = new JdbcForwardingOutbox(dataSource);

        // Stage 17 finding (unchanged): keep the JDBC / flyway autoconfig that agent-bus
        // leaks onto the shared test classpath out of agent-runtime's pure-in-memory
        // server context. Spring Boot 4 repackaged the autoconfigure class names.
        System.setProperty("spring.autoconfigure.exclude",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
              + "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,"
              + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration");

        runtime = RuntimeApp.create(new FailingHandler()).run(LocalA2aRuntimeHost.port(0));
        port = runtime.port();
        assertThat(port).as("real LocalA2aRuntimeHost bound a real ephemeral port").isPositive();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (runtime != null) {
            runtime.close();
        }
        if (pg != null) {
            pg.close();
        }
        System.clearProperty("spring.autoconfigure.exclude");
    }

    /**
     * Scenario 1 — a remote agent's terminal FAILED Task routes end to end to a DLQ
     * record carrying {@code remote_task_failed}. The {@link FailingHandler} reaches
     * FAILED on the REAL server; the real SSE FAILED frame is classified by
     * {@link A2aForwardingDeliveryPort} (Stage 18 MI18-002) as a non-retryable
     * {@link ForwardingFailureCode#REMOTE_TASK_FAILED} → direct DLQ, not a retry.
     */
    @Test
    void dispatch_loop_dlqs_real_runtime_failed_task() throws SQLException {
        String tenant = "tenant-fail";
        String route = "route-fail";
        String messageId = "msg-fail";
        long now = System.currentTimeMillis();

        ForwardingReceipt receipt = outbox.enqueue(envelope(tenant, messageId, route),
                "svc-src", "svc-tgt", now);
        assertThat(receipt.accepted()).isTrue();

        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                workerFor(route), oneTickThenStop(now), ForwardingDispatchLoop.NO_BACKOFF);

        ForwardingDispatcherWorker.DispatchTickResult tick = loop.run(tenant, 5, "worker-fail", 60_000);

        assertThat(tick.dlqd()).as("real FAILED terminal routed to DLQ end to end").isEqualTo(1);
        assertThat(tick.acked()).isZero();
        assertThat(tick.retried()).isZero();
        assertThat(tick.expired()).isZero();
        // Self-consistency invariant (ForwardingDispatcherWorker.DispatchTickResult).
        assertThat(tick.claimed())
                .as("tick counts stay self-consistent end to end")
                .isEqualTo(tick.acked() + tick.retried() + tick.dlqd() + tick.expired() + tick.skipped());

        assertThat(outbox.statusOf(id(messageId), tenant))
                .as("the real FAILED round-tripped to a persisted DLQ")
                .isEqualTo(ForwardingStatus.Outbox.DLQ);
        Map<String, Object> row = outboxRow(tenant, messageId);
        assertThat(row.get("last_failure_code"))
                .as("REMOTE_TASK_FAILED wire code persisted on the DLQ record")
                .isEqualTo(ForwardingFailureCode.REMOTE_TASK_FAILED.wireCode());
    }

    /**
     * Scenario 2 — an unreachable route (an endpoint nothing listens on) fails at the
     * socket with a real connection refusal, classified retryable
     * {@link ForwardingFailureCode#RECEIVER_UNAVAILABLE} (infra layer, not a remote
     * task failure), so the record is scheduled for retry with attempt_count = 1 and a
     * future next_attempt_at. No runtime is on this path — the failure precedes any
     * real server.
     */
    @Test
    void dispatch_loop_retries_when_route_unreachable() throws Exception {
        String tenant = "tenant-retry";
        String route = "route-retry";
        String messageId = "msg-retry";
        long now = System.currentTimeMillis();

        outbox.enqueue(envelope(tenant, messageId, route), "svc-src", "svc-tgt", now);

        int deadPort = freeUnusedPort();
        ForwardingEndpointResolver resolver = new MapEndpointResolver(
                Map.of(route, "http://localhost:" + deadPort + "/a2a"));
        A2aForwardingDeliveryPort delivery = new A2aForwardingDeliveryPort(resolver,
                new A2aForwardingProperties(2_000L, "X-Tenant-Id"));
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(outbox, outbox, delivery);
        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                worker, oneTickThenStop(now), ForwardingDispatchLoop.NO_BACKOFF);

        ForwardingDispatcherWorker.DispatchTickResult tick = loop.run(tenant, 5, "worker-retry", 60_000);

        assertThat(tick.retried()).as("unreachable route retried end to end").isEqualTo(1);
        assertThat(tick.acked()).isZero();
        assertThat(tick.dlqd()).isZero();
        assertThat(tick.claimed())
                .as("tick counts stay self-consistent end to end")
                .isEqualTo(tick.acked() + tick.retried() + tick.dlqd() + tick.expired() + tick.skipped());

        assertThat(outbox.statusOf(id(messageId), tenant))
                .isEqualTo(ForwardingStatus.Outbox.RETRY_SCHEDULED);
        Map<String, Object> row = outboxRow(tenant, messageId);
        assertThat(row.get("last_failure_code"))
                .isEqualTo(ForwardingFailureCode.RECEIVER_UNAVAILABLE.wireCode());
        assertThat(row.get("attempt_count"))
                .as("first retry attempt recorded").isEqualTo(1);
        assertThat(row.get("next_attempt_at"))
                .as("retry scheduled with a future nextAttemptAt").isNotNull();
    }

    // ---- helpers -----------------------------------------------------------

    /** The real /a2a JSON-RPC + SSE endpoint of the booted (FailingHandler) runtime. */
    private static String endpoint() {
        return "http://localhost:" + port + "/a2a";
    }

    /**
     * A worker whose delivery port resolves {@code route} to the real FailingHandler
     * runtime. The {@link JdbcForwardingOutbox} is BOTH the claim port and the state
     * port (it implements both SPIs). Defaults: {@code DispatchLeasePolicy.DISABLED} +
     * {@code ForwardingRetryPolicy.DEFAULT} + {@code ALWAYS_CLOSED} breaker.
     */
    private ForwardingDispatcherWorker workerFor(String route) {
        ForwardingEndpointResolver resolver = new MapEndpointResolver(Map.of(route, endpoint()));
        A2aForwardingDeliveryPort delivery = new A2aForwardingDeliveryPort(resolver,
                new A2aForwardingProperties(10_000L, "X-Tenant-Id"));
        return new ForwardingDispatcherWorker(outbox, outbox, delivery);
    }

    /** TickSource that yields {@code instant} once then stops (see Stage 17 IT). */
    private static ForwardingDispatchLoop.TickSource oneTickThenStop(long instant) {
        boolean[] ran = {false};
        return () -> {
            if (ran[0]) {
                return OptionalLong.empty();
            }
            ran[0] = true;
            return OptionalLong.of(instant);
        };
    }

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
     * it. The window between close and the delivery's connect is free of a listener, so
     * the real transport gets a genuine connection refusal — no MockWebServer, mirroring
     * a route whose target agent-runtime is down.
     */
    private static int freeUnusedPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /**
     * Raw projection of the persisted outbox row — the port exposes no per-record read
     * path ({@code claimDue} leases), so the IT reads {@code last_failure_code} /
     * {@code attempt_count} / {@code next_attempt_at} directly to assert the on-disk
     * failure code / retry state.
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

    /**
     * Minimal {@link AgentRuntimeHandler} that reaches a terminal FAILED Task on every
     * execute — the real {@code A2aResultRouter} turns
     * {@link AgentExecutionResult#failed(String, String)} into a Task FAILED state,
     * which the real /a2a SSE encodes as a FAILED frame, which
     * {@link A2aForwardingDeliveryPort} (Stage 18 MI18-002) now classifies as
     * dlq(REMOTE_TASK_FAILED). Mirrors {@code C3ForwardingEndToEndIntegrationTest.StubHandler}
     * but fails instead of completes.
     */
    private static final class FailingHandler implements AgentRuntimeHandler {
        @Override
        public String agentId() {
            return "failing";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of(Map.of("result_type", "answer", "output", "will-fail"));
        }

        @Override
        public StreamAdapter resultAdapter() {
            // Every raw result becomes a FAILED terminal — drives the real server to a
            // terminal FAILED Task (a business failure, not a transport failure).
            return rawResults -> rawResults.map(
                    raw -> AgentExecutionResult.failed("forced", "forced failure"));
        }
    }
}
