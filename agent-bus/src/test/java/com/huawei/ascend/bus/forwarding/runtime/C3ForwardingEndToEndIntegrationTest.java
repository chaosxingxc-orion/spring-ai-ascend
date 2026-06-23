package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.MapEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.a2a.A2aForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.a2a.A2aForwardingProperties;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
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
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 17 (MI17-005) — the first end-to-end integration across the
 * agent-bus &harr; agent-runtime module boundary.
 *
 * <p>Boots REAL infrastructure on both sides of the C3 forwarding chain and drives
 * the full path with no fakes beyond the agent handler:
 * <pre>
 *   JdbcForwardingOutbox  (real PostgreSQL — embedded-postgres + Flyway)
 *        &darr; enqueue
 *   ForwardingDispatchLoop &rarr; ForwardingDispatcherWorker  (claim, lease-guarded)
 *        &darr; deliver
 *   A2aForwardingDeliveryPort  (real A2A JSON-RPC client — the Stage 15 adapter)
 *        &darr; SendStreamingMessage  +  X-Tenant-Id header
 *   LocalA2aRuntimeHost  (REAL Spring Boot A2A server, port 0) + StubHandler
 *        &darr; Task &rarr; COMPLETED
 *   SSE COMPLETED frame  &rarr;  worker markAcked
 *        &darr;
 *   outbox record &rarr; ACKED
 * </pre>
 *
 * <p>This replaces Stage 15's MockWebServer (which proved the delivery port emits a
 * byte-identical request and maps the remote Task lifecycle onto
 * {@link com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult}) with a REAL
 * agent-runtime server. The Stage 17 increment proves the JSON-RPC / SSE bytes the
 * real {@code JSONRPCTransport} client and the real {@code A2aJsonRpcController}
 * server exchange actually complete the Task and round-trip to an outbox ACK — no
 * stub transport, no mock server, no in-memory outbox.
 *
 * <p>Driving the chain through {@link ForwardingDispatchLoop} (Stage 10 MI10-004)
 * exercises the loop's injected {@link ForwardingDispatchLoop.TickSource} and
 * {@link ForwardingDispatchLoop.IdleStrategy} seams: the loop holds no clock,
 * scheduler or thread — the test supplies a {@code TickSource} that yields one tick
 * instant then stops, so the loop runs exactly one {@code worker.runOnce} tick and
 * the {@code DispatchTickResult} is that tick's outcome.
 *
 * <p><b>Tenant continuity (Rule R-C.c).</b> Stage 15 already asserted the
 * {@code X-Tenant-Id} header value at the wire (see
 * {@code A2aForwardingDeliveryPortMockWebServerTest}). Stage 17 does not repeat
 * that header assertion here — the real controller consumes the header in the A2A
 * protocol layer, invisible to the handler — but proves the tenant boundary holds
 * end to end via {@link #dispatch_loop_isolates_tenants_end_to_end}: a tenant A
 * loop driving two routes that resolve to the SAME real host never touches tenant
 * B's record (no cross-tenant fallback, &sect;6.2).
 *
 * <p><b>Why a re-declared StubHandler, not agent-runtime's.</b> That module's own
 * {@code RuntimeAppTest.StubHandler} is a private nested class. Re-declaring the
 * same minimal handler here keeps this IT free of agent-runtime test internals and
 * keeps the production dependency clean —
 * {@code AgentBusDependencyBoundaryTest.bus_does_not_depend_on_agent_runtime}
 * guards {@code com.huawei.ascend.bus..} (production) against
 * {@code com.huawei.ascend.runtime..}; this test-only reference is why that guard
 * exists.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage16-review-and-stage17-plan.md}
 * &sect;4 MI17-005.
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
class C3ForwardingEndToEndIntegrationTest {

    private static EmbeddedPostgres pg;
    private static JdbcForwardingOutbox outbox;
    private static RunningRuntime runtime;
    private static int port;

    @BeforeAll
    static void bootPostgresAndRealRuntime() throws Exception {
        // Real PostgreSQL: same in-process server + real Flyway migration as
        // ForwardingJdbcIntegrationTest (the host's Docker path is dead; see pom).
        pg = EmbeddedPostgres.builder().start();
        DataSource dataSource = pg.getPostgresDatabase();
        Flyway.configure().dataSource(dataSource).load().migrate();
        outbox = new JdbcForwardingOutbox(dataSource);

        // Stage 17 finding: agent-runtime's A2A server Spring context is pure
        // in-memory (A2A SDK task store / event queue — no JDBC), so its own tests
        // never trip DataSourceAutoConfiguration. But once agent-bus's
        // spring-boot-starter-jdbc + postgres driver + flyway (Stage 12) are on the
        // shared test classpath, DataSourceAutoConfiguration / FlywayAutoConfiguration
        // fire against the real host's context and fail for lack of a
        // spring.datasource.url. Excluded here via a system property — the only
        // property source that sits above LocalA2aRuntimeHost's package-private
        // defaultProperties (the public port(int) factory exposes no property hook).
        // The agent-bus side keeps using its own embedded-postgres DataSource
        // directly; this only keeps the leaked JDBC autoconfig out of the
        // agent-runtime context. Recorded for agent-runtime: LocalA2aRuntimeHost is
        // sensitive to a JDBC-bearing shared classpath — a co-deployed consumer
        // with jdbc starter would hit the same failure and should exclude these.
        // NB the class names follow Spring Boot 4's autoconfigure repackaging
        // (jdbc → org.springframework.boot.jdbc.autoconfigure, flyway →
        // org.springframework.boot.flyway.autoconfigure); the Spring Boot 3
        // org.springframework.boot.autoconfigure.jdbc.* / .flyway.* names no longer
        // exist, so excluding those would silently do nothing.
        System.setProperty("spring.autoconfigure.exclude",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
              + "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,"
              + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration");

        // Real Spring Boot A2A server on an ephemeral port. Replaces Stage 15's
        // MockWebServer with the genuine LocalA2aRuntimeHost + controller + SSE.
        // The public port(int) factory is the cross-package entry point (the
        // Map/args constructor is package-private to com.huawei.ascend.runtime.app).
        runtime = RuntimeApp.create(new StubHandler()).run(LocalA2aRuntimeHost.port(0));
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
     * Happy path: one dispatch-loop tick drives a PENDING outbox record through the
     * REAL agent-runtime server to a terminal ACK. The {@code TickSource} yields one
     * tick instant then stops, so the aggregate {@code DispatchTickResult} is exactly
     * that single tick — proving the loop &rarr; worker &rarr; delivery port &rarr;
     * real /a2a &rarr; COMPLETED &rarr; markAcked chain end to end, and that the tick
     * stays self-consistent ({@code claimed == acked + retried + dlqd + expired + skipped}).
     */
    @Test
    void dispatch_loop_drives_real_runtime_to_acked() {
        String tenant = "tenant-loop";
        String route = "route-loop";
        String messageId = "msg-loop";
        long now = System.currentTimeMillis();

        ForwardingReceipt receipt = outbox.enqueue(envelope(tenant, messageId, route),
                "svc-src", "svc-tgt", now);
        assertThat(receipt.accepted()).isTrue();
        assertThat(outbox.statusOf(id(messageId), tenant)).isEqualTo(ForwardingStatus.Outbox.PENDING);

        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                workerFor(route), oneTickThenStop(now), ForwardingDispatchLoop.NO_BACKOFF);

        ForwardingDispatcherWorker.DispatchTickResult tick = loop.run(tenant, 5, "worker-loop", 60_000);

        assertThat(tick.claimed()).as("exactly one record claimed").isEqualTo(1);
        assertThat(tick.acked()).as("that record acked through the real runtime").isEqualTo(1);
        assertThat(tick.retried()).isZero();
        assertThat(tick.dlqd()).isZero();
        assertThat(tick.expired()).isZero();
        assertThat(tick.skipped()).isZero();
        // Self-consistency invariant (ForwardingDispatcherWorker.DispatchTickResult).
        assertThat(tick.claimed())
                .as("tick counts stay self-consistent end to end")
                .isEqualTo(tick.acked() + tick.retried() + tick.dlqd() + tick.expired() + tick.skipped());
        assertThat(outbox.statusOf(id(messageId), tenant))
                .as("the real COMPLETED round-tripped to a persisted ACK")
                .isEqualTo(ForwardingStatus.Outbox.ACKED);
    }

    /**
     * End-to-end tenant isolation (Rule R-C.c). Two tenants each enqueue a record;
     * both routes resolve to the SAME real host. A tenant A dispatch loop must claim
     * and ACK only tenant A's record — tenant B's stays PENDING. The tenant boundary
     * holds across the claim filter, the delivery port, and the real server, with no
     * cross-tenant fallback (&sect;6.2).
     */
    @Test
    void dispatch_loop_isolates_tenants_end_to_end() {
        String tenantA = "tenant-iso-a";
        String tenantB = "tenant-iso-b";
        String routeA = "route-iso-a";
        String routeB = "route-iso-b";
        long now = System.currentTimeMillis();

        outbox.enqueue(envelope(tenantA, "msg-iso-a", routeA), "svc-src", "svc-tgt", now);
        outbox.enqueue(envelope(tenantB, "msg-iso-b", routeB), "svc-src", "svc-tgt", now);
        assertThat(outbox.statusOf(id("msg-iso-a"), tenantA)).isEqualTo(ForwardingStatus.Outbox.PENDING);
        assertThat(outbox.statusOf(id("msg-iso-b"), tenantB)).isEqualTo(ForwardingStatus.Outbox.PENDING);

        // Both routes point at the SAME real host — tenant continuity rides on the
        // claim filter + the X-Tenant-Id header (asserted in Stage 15), not on the
        // physical endpoint. A tenant A loop must never reach tenant B's record.
        ForwardingEndpointResolver resolver = new MapEndpointResolver(Map.of(
                routeA, endpoint(),
                routeB, endpoint()));
        A2aForwardingDeliveryPort delivery = new A2aForwardingDeliveryPort(resolver,
                new A2aForwardingProperties(10_000L, "X-Tenant-Id"));
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(outbox, outbox, delivery);
        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(
                worker, oneTickThenStop(now), ForwardingDispatchLoop.NO_BACKOFF);

        ForwardingDispatcherWorker.DispatchTickResult tickA = loop.run(tenantA, 5, "worker-iso-a", 60_000);

        assertThat(tickA.acked()).as("tenant A's record acked through the real runtime").isEqualTo(1);
        assertThat(outbox.statusOf(id("msg-iso-a"), tenantA)).isEqualTo(ForwardingStatus.Outbox.ACKED);
        assertThat(outbox.statusOf(id("msg-iso-b"), tenantB))
                .as("tenant B's record untouched — no cross-tenant fallback (§6.2)")
                .isEqualTo(ForwardingStatus.Outbox.PENDING);
    }

    // ---- helpers -----------------------------------------------------------

    /** The real /a2a JSON-RPC + SSE endpoint of the booted runtime. */
    private static String endpoint() {
        return "http://localhost:" + port + "/a2a";
    }

    /**
     * A worker whose delivery port resolves {@code route} to the real runtime and
     * carries the tenant as {@code X-Tenant-Id}. The JdbcForwardingOutbox is BOTH the
     * claim port and the state port (it implements both SPIs).
     */
    private ForwardingDispatcherWorker workerFor(String route) {
        ForwardingEndpointResolver resolver = new MapEndpointResolver(Map.of(route, endpoint()));
        A2aForwardingDeliveryPort delivery = new A2aForwardingDeliveryPort(resolver,
                new A2aForwardingProperties(10_000L, "X-Tenant-Id"));
        return new ForwardingDispatcherWorker(outbox, outbox, delivery);
    }

    /**
     * TickSource that yields {@code instant} once then stops — drives exactly one
     * worker tick so the aggregate DispatchTickResult is that single tick's outcome.
     */
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
     * Minimal {@link AgentRuntimeHandler} that reaches COMPLETED on every execute —
     * mirrors agent-runtime's own {@code RuntimeAppTest.StubHandler} (private there,
     * so re-declared here). The point of Stage 17 is NOT to exercise a real agent; it
     * is to prove the agent-bus forwarding chain drives a REAL A2A server (not Stage
     * 15's MockWebServer) to a terminal COMPLETED Task and back to an outbox ACK.
     */
    private static final class StubHandler implements AgentRuntimeHandler {
        @Override
        public String agentId() {
            return "stub";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of(Map.of("result_type", "answer", "output", "ok"));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> rawResults.map(raw -> AgentExecutionResult.completed("ok"));
        }
    }
}
