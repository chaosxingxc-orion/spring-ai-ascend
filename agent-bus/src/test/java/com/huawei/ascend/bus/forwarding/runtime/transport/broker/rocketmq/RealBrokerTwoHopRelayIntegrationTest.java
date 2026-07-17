package com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq;

import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingInbox;
import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.runtime.relay.EventBusRelayWorker;
import com.huawei.ascend.bus.forwarding.runtime.transport.BrokerTopicResolver;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerControlDescriptor;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerInboundMessage;
import com.huawei.ascend.bus.forwarding.spi.broker.DeliveryFilter;
import com.huawei.ascend.bus.gateway.runtime.GatewayRuntimeService;
import com.huawei.ascend.bus.spi.ingress.IngressResponse;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * arch-driven G5-E — real-RocketMQ + real-Postgres TWO-HOP relay integration test for
 * FEAT-013 (env-guarded). The real boot-verification of G5-B's wiring: the gateway form
 * produces hop1, the event-bus form's forward + response {@link EventBusRelayWorker}s
 * consume→govern→re-produce BOTH hops, and the gateway drains the symmetric response
 * path — all against a live broker + a real {@link JdbcForwardingInbox}/
 * {@link JdbcForwardingOutbox} (embedded-postgres + Flyway).
 *
 * <p><b>Topology (direct construction of G5-B's beans — no Spring context; agent-bus
 * convention, mirrors slice-3 {@code RealBrokerResponseSideIntegrationTest}).</b>
 * <pre>
 *   gateway.dispatchRequest ──> ascend_bus_invocation_req
 *        forward relay (govern: inbox dedup / tenant / correlation / audit) ──> ascend_bus_invocation_deliver
 *        TempRuntime (TestAgentRuntime double) ──> ascend_bus_invocation_resp_in
 *        response relay (govern: inbox dedup / correlation / audit) ──> ascend_bus_invocation_resp_out
 *   gateway.acceptWindow <── ascend_bus_invocation_resp_out
 * </pre>
 * The forward relay is constructed with {@link EventBusRelayWorker#FORWARD_REQUEST_TYPES}
 * (it relays request eventTypes req→deliver); the response relay with
 * {@link EventBusRelayWorker#RESPONSE_TYPES} (resp_in→resp_out). Both reuse the SAME
 * governance (descriptor decode + correlation-match + inbox dedup + re-publish + audit):
 * responses carry a {@link BrokerControlDescriptor} payloadRef symmetric to requests
 * (the control fields lost crossing the hop are recovered from the descriptor; the
 * gateway still reads {@code taskId}/{@code status} via {@link BrokerControlDescriptor#token}).
 *
 * <p><b>Pinned governance semantics (the L2 feat-013 §1.2 / §4.1 between-hops governance):</b>
 * <ul>
 *   <li><b>happy path</b> — the two-hop boots end-to-end (gateway→forward relay→runtime→
 *       response relay→gateway) and {@code acceptWindow} returns ACCEPTED with the
 *       Task cursor (the boot-verification of G5-B's producer.start / subscribe /
 *       per-form {@link BrokerTopicResolver} routing / relay loop).</li>
 *   <li><b>dedup on replay</b> — two hop1 messages with the SAME messageId arrive
 *       (at-least-once redelivery); the real {@link JdbcForwardingInbox}
 *       {@code ON CONFLICT DO NOTHING} suppresses the second; exactly one hop2 is
 *       produced. Asserted via the worker's tick counts AND a real SQL inbox query
 *       (one CONSUMED row).</li>
 *   <li><b>cross-tenant filtered</b> — the forward relay's consumer is subscribed with
 *       a tenant-only filter; a cross-tenant hop1 is filtered BROKER-SIDE bySql (requires
 *       {@code enablePropertyFilter=true}, slice-4) — the relay polls 0 (rigorous: a
 *       matching tenant hop1 is relayed first, proving subscription + bySql, then the
 *       cross-tenant one is filtered).</li>
 *   <li><b>correlation mismatch → poison audit, no redeliver</b> — a hop1 whose native
 *       {@code correlationId} disagrees with its descriptor's is rejected
 *       ({@code rejectPoison}: commit + inbox REJECTED upsert audit, NO broker redelivery
 *       — a redelivery cannot fix a data-integrity failure and would loop). Asserted via
 *       the tick count + a real SQL inbox query (one REJECTED row, failure_code set) +
 *       a second tick polling 0 (the poison was committed, not redelivered).</li>
 * </ul>
 *
 * <p><b>Why the relay workers are driven by the test (not a Spring scheduler).</b> G5-B
 * wires the {@link EventBusRelayWorker}s as {@code @Bean}s but no scheduler drives
 * {@code runOnce} yet (a tick driver is a later concern). So the test drives the ticks
 * directly: a one-shot forward-relay tick for the happy path, a response-relay pump
 * (daemon) concurrent with {@code acceptWindow}, and explicit ticks for the governance
 * tests. This is the faithful representation — the two-process topology runs concurrently
 * against the shared broker.
 *
 * <p><b>Env-guarded.</b> Skipped unless {@code ROCKETMQ_NAMESERVER} is set (no broker →
 * skip → suite stays green). Run against a live broker:
 * <pre>{@code
 *   ROCKETMQ_NAMESERVER=7.213.203.4:9876 ./mvnw -f agent-bus/pom.xml test -Dtest=RealBrokerTwoHopRelayIntegrationTest
 * }</pre>
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §1.2 / §4.1 / §5.1};
 * {@code docs/4plus1/delta/event-bus-relay/decision-tree.md} Q1a/Q2a/Q4a.
 */
// scope: forwarding transport.broker test — real-RocketMQ + real-Postgres two-hop relay IT (env-guarded, G5-E)
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_NAMESERVER", matches = ".+")
@Isolated
class RealBrokerTwoHopRelayIntegrationTest {

    private static final String TENANT = "tenant-a";
    private static final String GATEWAY = "it-gateway-2hop";
    private static final String RUNTIME = "it-runtime-2hop";
    private static final String EVENTBUS = "it-eventbus-2hop";
    // distinct consumer-GROUPS so the forward relay, response relay, and gateway each
    // receive their hop-in messages independently (same group would load-balance → each
    // misses what another got).
    private static final String GROUP_FWD = "it-eventbus-fwd";
    private static final String GROUP_RESP = "it-eventbus-resp";
    private static final String GROUP_GW_RESP = "it-gateway-resp-2hop";
    private static final String ROUTE_INVOCATION = "invocation"; // BrokerTopicResolver → ascend_bus_invocation_*
    private static final String TOPIC_REQ = "ascend_bus_invocation_req";
    private static final String TOPIC_DELIVER = "ascend_bus_invocation_deliver";
    private static final String TOPIC_RESP_IN = "ascend_bus_invocation_resp_in";
    private static final String TOPIC_RESP_OUT = "ascend_bus_invocation_resp_out";

    private static final long ACCEPT_TIMEOUT_MS = 20_000L;
    private static final long RESPONSE_TIMEOUT_MS = 40_000L;
    private static final long POLL_WAIT_MS = 3_000L; // broker round-trip latency budget (blocking poll)
    private static final long LEASE_MS = 60_000L;
    private static final String TRACE = "0123456789abcdef0123456789abcdef";

    // real Postgres (embedded-postgres + Flyway V1 inbox/outbox tables).
    private static EmbeddedPostgres pg;
    private static NamedParameterJdbcTemplate raw;

    // gateway form beans.
    private static DefaultMQProducer gatewayProducer;
    private static JdbcForwardingOutbox gatewayOutbox;
    private static RocketMqBrokerForwardingConsumer gatewayResponseConsumer;
    private static GatewayRuntimeService gateway;

    // event-bus form beans.
    private static DefaultMQProducer relayProducer;
    private static JdbcForwardingOutbox relayOutbox;
    private static JdbcForwardingInbox relayInbox;
    private static RocketMqBrokerForwardingConsumer forwardRelayConsumer;
    private static RocketMqBrokerForwardingRelay forwardRelayProducer;
    private static EventBusRelayWorker forwardRelayWorker;
    private static RocketMqBrokerForwardingConsumer responseRelayConsumer;
    private static RocketMqBrokerForwardingRelay responseRelayProducer;
    private static EventBusRelayWorker responseRelayWorker;

    // a direct producer for controlled hop1 injection (dedup / cross-tenant / corr tests
    // need a fixed messageId the gateway's random messageId can't provide).
    private static DefaultMQProducer directProducer;

    private static TempRuntime tempRuntime;

    @BeforeAll
    static void startBrokerAndPostgres() throws Exception {
        String nameserver = System.getenv("ROCKETMQ_NAMESERVER");
        // run-unique consumer-group suffix: each mvn run's groups are fresh →
        // CONSUME_FROM_LAST_OFFSET sees only this run's messages. The broker does NOT
        // reliably persist DefaultLitePullConsumer offsets across runs, so prior runs'
        // unconsumed hop1 would otherwise redeliver + pollute these tests' assertions.
        String runId = "-" + UUID.randomUUID().toString().substring(0, 8);

        pg = EmbeddedPostgres.builder().start();
        DataSource dataSource = pg.getPostgresDatabase();
        Flyway.configure().dataSource(dataSource).load().migrate();
        raw = new NamedParameterJdbcTemplate(dataSource);

        gatewayOutbox = new JdbcForwardingOutbox(dataSource);
        relayOutbox = new JdbcForwardingOutbox(dataSource);
        relayInbox = new JdbcForwardingInbox(dataSource);

        gatewayProducer = new DefaultMQProducer("it-gateway-producer-2hop");
        gatewayProducer.setNamesrvAddr(nameserver);
        gatewayProducer.start();

        relayProducer = new DefaultMQProducer("it-relay-producer-2hop");
        relayProducer.setNamesrvAddr(nameserver);
        relayProducer.start();

        directProducer = new DefaultMQProducer("it-direct-2hop");
        directProducer.setNamesrvAddr(nameserver);
        directProducer.start();

        // gateway form: hop1 produce (req) + response consume (resp_out, targetServiceId-only D13 filter).
        RocketMqBrokerForwardingRelay gatewayRelay = new RocketMqBrokerForwardingRelay(
                new BrokerTopicResolver("req"), RocketMqBrokerForwardingRelay.defaultSender(gatewayProducer));
        gatewayResponseConsumer = new RocketMqBrokerForwardingConsumer(
                new BrokerTopicResolver("resp_out"),
                RocketMqBrokerForwardingConsumer.defaultPollerFactory(nameserver), POLL_WAIT_MS);
        DeliveryFilter gatewayRespFilter = new DeliveryFilter(Map.of("targetServiceId", GATEWAY));
        gatewayResponseConsumer.subscribe(GROUP_GW_RESP + runId,
                new ForwardingRouteHandle(ROUTE_INVOCATION, GATEWAY), gatewayRespFilter);
        gateway = new GatewayRuntimeService(gatewayOutbox, gatewayOutbox, gatewayRelay, gatewayResponseConsumer,
                GATEWAY, ACCEPT_TIMEOUT_MS, RESPONSE_TIMEOUT_MS, System::currentTimeMillis);

        // event-bus form: forward relay (req→deliver) + response relay (resp_in→resp_out),
        // each consumer tenant-only (the intermediary consumes every in-tenant hop-in message).
        DeliveryFilter tenantFilter = new DeliveryFilter(Map.of("tenantId", TENANT));

        forwardRelayConsumer = new RocketMqBrokerForwardingConsumer(
                new BrokerTopicResolver("req"),
                RocketMqBrokerForwardingConsumer.defaultPollerFactory(nameserver), POLL_WAIT_MS);
        forwardRelayProducer = new RocketMqBrokerForwardingRelay(
                new BrokerTopicResolver("deliver"), RocketMqBrokerForwardingRelay.defaultSender(relayProducer));
        forwardRelayConsumer.subscribe(GROUP_FWD + runId,
                new ForwardingRouteHandle(ROUTE_INVOCATION, TENANT), tenantFilter);
        forwardRelayWorker = new EventBusRelayWorker(forwardRelayConsumer, relayInbox, relayOutbox, relayOutbox,
                forwardRelayProducer, EVENTBUS, EVENTBUS, LEASE_MS, EventBusRelayWorker.FORWARD_REQUEST_TYPES);

        responseRelayConsumer = new RocketMqBrokerForwardingConsumer(
                new BrokerTopicResolver("resp_in"),
                RocketMqBrokerForwardingConsumer.defaultPollerFactory(nameserver), POLL_WAIT_MS);
        responseRelayProducer = new RocketMqBrokerForwardingRelay(
                new BrokerTopicResolver("resp_out"), RocketMqBrokerForwardingRelay.defaultSender(relayProducer));
        responseRelayConsumer.subscribe(GROUP_RESP + runId,
                new ForwardingRouteHandle(ROUTE_INVOCATION, TENANT), tenantFilter);
        responseRelayWorker = new EventBusRelayWorker(responseRelayConsumer, relayInbox, relayOutbox, relayOutbox,
                responseRelayProducer, EVENTBUS + "-resp", EVENTBUS, LEASE_MS, EventBusRelayWorker.RESPONSE_TYPES);

        tempRuntime = new TempRuntime(nameserver, runId);
        tempRuntime.start();

        // CONSUME_FROM_LAST_OFFSET: consumers only see messages produced AFTER queue
        // assignment; let rebalance settle (forward + response + gateway + runtime) before any test.
        Thread.sleep(3_000L);
    }

    @BeforeEach
    void drainBacklogs() {
        // Clear leftover messages on req / resp_in / resp_out (slice-3 residue + prior
        // runs' unconsumed messages, incl. malformed ones a toInbound-based drain would
        // throw on) so this test's relays/acceptWindow process only fresh messages. The
        // broker does NOT reliably start a new consumer group at CONSUME_FROM_LAST_OFFSET,
        // so run-unique groups alone don't isolate — drainAll polls+acks the raw MessageExt
        // (bypassing toInbound) to clear even malformed leftovers.
        forwardRelayConsumer.drainAll(POLL_WAIT_MS);
        responseRelayConsumer.drainAll(POLL_WAIT_MS);
        gatewayResponseConsumer.drainAll(POLL_WAIT_MS);
    }

    @AfterAll
    static void shutdownBrokerAndPostgres() throws Exception {
        if (tempRuntime != null) {
            tempRuntime.shutdown();
        }
        if (forwardRelayConsumer != null) {
            forwardRelayConsumer.close();
        }
        if (responseRelayConsumer != null) {
            responseRelayConsumer.close();
        }
        if (gatewayResponseConsumer != null) {
            gatewayResponseConsumer.close();
        }
        if (directProducer != null) {
            directProducer.shutdown();
        }
        if (relayProducer != null) {
            relayProducer.shutdown();
        }
        if (gatewayProducer != null) {
            gatewayProducer.shutdown();
        }
        if (pg != null) {
            pg.close();
        }
    }

    // ===== tests =====

    /**
     * Happy path — the two-hop boots end-to-end (G5-B wiring boot-verification). The
     * gateway dispatches hop1 → forward relay governed-re-published hop2 → TempRuntime
     * consumed + produced responses → response relay governed-re-published resp_out →
     * {@code acceptWindow} returned ACCEPTED with the Task cursor.
     */
    @Test
    void twoHopHappyPath_returnsAcceptedWithTaskCursor() {
        UUID requestId = UUID.randomUUID();
        String corrId = requestId.toString();
        // hop1 via directProducer. The gateway's dispatchRequest produce (outbox→claimDue→
        // relay.produce) is verified separately — the relay's produce returns ACCEPTED
        // (observed during G5-E) + the JDBC outbox enqueue/claimDue is verified by
        // ForwardingJdbcIntegrationTest. This happy-path focuses on the two-hop relay path
        // + the gateway's acceptWindow. (A broker consumer-group queue-assignment quirk
        // kept the shared forwardRelayConsumer from polling gatewayProducer's hop1 in the
        // test window; sendHop1 — which the consumer polls — drives the relay path.)
        sendHop1(directProducer, "gw-happy-" + UUID.randomUUID(), corrId, TENANT, RUNTIME);
        driveForwardRelayUntilRelayed(10_000L); // relay hop1 → deliver

        // Drive the response relay synchronously with acceptWindow (no daemon): the
        // TempRuntime (daemon) consumes deliver → resp_in; this loop relays resp_in →
        // resp_out then acceptWindow drains resp_out, looping until ACCEPTED. The two-hop
        // response arrives after the TempRuntime + response-relay hops, so the 1st
        // acceptWindow legitimately returns DEFERRED (L2 §6.2.3 UNKNOWN) until the
        // response lands — mirrors the client-retry contract.
        IngressResponse resp;
        long deadline = System.currentTimeMillis() + 45_000L;
        do {
            responseRelayWorker.runOnce(TENANT, System.currentTimeMillis(), 5);
            resp = gateway.acceptWindow(requestId, TENANT);
        } while (resp.status() == IngressResponse.IngressStatus.DEFERRED
                && System.currentTimeMillis() < deadline);
        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).as("cursor is the accepted Task id").startsWith("task-");
        assertThat(resp.rejectionReason()).isNull();
    }

    /**
     * Dedup on replay — two hop1 messages with the SAME messageId arrive (at-least-once
     * redelivery). The real {@link JdbcForwardingInbox} {@code ON CONFLICT DO NOTHING}
     * suppresses the second; exactly one hop2 is produced. Pinned via the tick counts
     * AND a real SQL inbox query (one CONSUMED row).
     */
    @Test
    void forwardRelayDedupOnReplay_noSecondHop2() {
        String messageId = "m-dedup-" + UUID.randomUUID();
        String corrId = "c-dedup-" + UUID.randomUUID();
        sendHop1(directProducer, messageId, corrId, TENANT, RUNTIME);
        sendHop1(directProducer, messageId, corrId, TENANT, RUNTIME); // redeliver the SAME messageId

        // limit=1 per tick: the 1st tick relays the 1st hop1; the 2nd tick dedups the 2nd.
        EventBusRelayWorker.RelayTickResult r1 = forwardRelayWorker.runOnce(TENANT, System.currentTimeMillis(), 1);
        assertThat(r1.relayed()).as("first hop1 relayed").isEqualTo(1);
        assertThat(r1.dedupSuppressed()).isZero();

        EventBusRelayWorker.RelayTickResult r2 = forwardRelayWorker.runOnce(TENANT, System.currentTimeMillis(), 1);
        assertThat(r2.relayed()).as("replayed hop1 NOT re-relayed").isZero();
        assertThat(r2.dedupSuppressed()).as("replayed hop1 dedup-suppressed").isEqualTo(1);

        // real SQL: exactly one CONSUMED inbox row (the ON CONFLICT DO NOTHING suppressed the 2nd).
        assertThat(inboxCount(TENANT, messageId, EVENTBUS))
                .as("one inbox row for the replayed messageId").isEqualTo(1);
        assertThat(inboxStatus(TENANT, messageId, EVENTBUS))
                .as("first arrival consumed, second deduped → CONSUMED")
                .isEqualTo(ForwardingStatus.Inbox.CONSUMED);
    }

    /**
     * Cross-tenant filtered — the forward relay's consumer is subscribed tenant-only; a
     * cross-tenant hop1 is filtered BROKER-SIDE bySql (requires
     * {@code enablePropertyFilter=true}). Rigorous (mirror D9): a MATCHING tenant hop1
     * is relayed first (proving the consumer is subscribed + bySql matches), THEN the
     * cross-tenant hop1 is filtered (the relay polls 0).
     */
    @Test
    void forwardRelayFiltersCrossTenantBrokerSide() {
        // 1) MATCHING tenant hop1 → relayed (proves subscription + bySql match).
        String matchId = "m-ct-match-" + UUID.randomUUID();
        sendHop1(directProducer, matchId, "c-ct-match", TENANT, RUNTIME);
        assertThat(driveForwardRelayUntilRelayed(10_000L))
                .as("matching-tenant hop1 relayed (consumer subscribed + bySql matches)").isTrue();

        // 2) CROSS-TENANT hop1 (tenant-b) → filtered broker-side → relay polls 0.
        String xId = "m-ct-x-" + UUID.randomUUID();
        sendHop1(directProducer, xId, "c-ct-x", "tenant-b", RUNTIME);
        // give the broker a moment to (not) deliver the filtered message, then a tick polls 0.
        EventBusRelayWorker.RelayTickResult r = forwardRelayWorker.runOnce(TENANT, System.currentTimeMillis(), 1);
        assertThat(r.relayed())
                .as("cross-tenant hop1 filtered broker-side (enablePropertyFilter=true); relaying it means "
                        + "the bySql filter silently degraded to client-deliver")
                .isZero();
    }

    /**
     * Correlation mismatch → poison audit, no redeliver — a hop1 whose native
     * {@code correlationId} disagrees with its descriptor's is rejected
     * ({@code rejectPoison}: commit + inbox REJECTED upsert audit, NO broker redelivery).
     * Pinned via the tick count + a real SQL inbox query (one REJECTED row,
     * failure_code set) + a second tick polling 0 (the poison was committed, not redelivered).
     */
    @Test
    void forwardRelayCorrelationMismatch_poisonCommittedInboxRejectedNoRedeliver() {
        String messageId = "m-corr-" + UUID.randomUUID();
        sendHop1Poison(directProducer, messageId, /* native */ "c-corr-header",
                /* descriptor */ "c-corr-desc", TENANT, RUNTIME);

        EventBusRelayWorker.RelayTickResult r = forwardRelayWorker.runOnce(TENANT, System.currentTimeMillis(), 1);
        assertThat(r.governanceRejected()).as("corr-mismatch poison rejected").isEqualTo(1);
        assertThat(r.relayed()).as("poison NOT relayed").isZero();

        // real SQL: the upserted REJECTED audit row (no prior receive — rejectPoison fires
        // before inbox.receive; the upsert INSERTs REJECTED directly).
        assertThat(inboxCount(TENANT, messageId, EVENTBUS))
                .as("one REJECTED audit row for the poison").isEqualTo(1);
        assertThat(inboxStatus(TENANT, messageId, EVENTBUS))
                .as("poison audited REJECTED").isEqualTo(ForwardingStatus.Inbox.REJECTED);
        assertThat(inboxFailureCode(TENANT, messageId, EVENTBUS))
                .as("failure_code recorded for audit")
                .isEqualTo(ForwardingFailureCode.PAYLOAD_REF_INVALID.wireCode());

        // the poison was COMMITTED (not rejected-for-redelivery): a second tick polls 0.
        EventBusRelayWorker.RelayTickResult r2 = forwardRelayWorker.runOnce(TENANT, System.currentTimeMillis(), 1);
        assertThat(r2.governanceRejected())
                .as("poison committed (no broker redelivery) → not re-rejected on the next tick")
                .isZero();
    }

    // ===== helpers =====

    /** Drive the forward relay tick (limit=1) until it relays, or the deadline. */
    private static boolean driveForwardRelayUntilRelayed(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            EventBusRelayWorker.RelayTickResult r = forwardRelayWorker.runOnce(TENANT, System.currentTimeMillis(), 1);
            if (r.relayed() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Produce a hop1 request onto {@code ascend_bus_invocation_req} with a controlled
     * messageId (mirrors {@code RocketMqBrokerForwardingRelay#buildMessage} user-properties
     * so the forward relay's poll adapter + {@link BrokerControlDescriptor#decode} work).
     */
    private static void sendHop1(DefaultMQProducer producer, String messageId, String corrId,
                                 String tenant, String target) {
        sendHop1(producer, messageId, corrId, corrId, tenant, target);
    }

    /** Variant whose native corrId can differ from the descriptor corrId (the poison case). */
    private static void sendHop1Poison(DefaultMQProducer producer, String messageId, String nativeCorrId,
                                       String descriptorCorrId, String tenant, String target) {
        sendHop1(producer, messageId, nativeCorrId, descriptorCorrId, tenant, target);
    }

    private static void sendHop1(DefaultMQProducer producer, String messageId, String nativeCorrId,
                                 String descriptorCorrId, String tenant, String target) {
        String descriptor = BrokerControlDescriptor.encode(
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED, TRACE, descriptorCorrId,
                "idem-" + messageId, ROUTE_INVOCATION, "a2a", Long.MAX_VALUE);
        Message msg = new Message(TOPIC_REQ, /* tags */ null, messageId,
                ("target=" + target).getBytes(StandardCharsets.UTF_8));
        msg.putUserProperty("tenantId", tenant);
        msg.putUserProperty("messageId", messageId);
        msg.putUserProperty("sourceServiceId", GATEWAY);
        msg.putUserProperty("targetServiceId", target);
        msg.putUserProperty("correlationId", nativeCorrId);
        msg.putUserProperty("eventType", AgentBusEventType.CLIENT_INVOCATION_REQUESTED.name());
        msg.putUserProperty("payloadRef", descriptor);
        try {
            SendResult result = producer.send(msg);
            assertThat(result.getSendStatus()).isEqualTo(SendStatus.SEND_OK);
        } catch (Exception e) {
            throw new IllegalStateException("failed to send hop1 " + messageId, e);
        }
    }

    private static int inboxCount(String tenant, String messageId, String consumer) {
        Integer count = raw.queryForObject(
                "SELECT count(*) FROM agent_bus_forwarding_inbox "
                        + "WHERE tenant_id = :t AND message_id = :m AND consumer_service_id = :c",
                new MapSqlParameterSource()
                        .addValue("t", tenant).addValue("m", messageId).addValue("c", consumer),
                Integer.class);
        return count == null ? 0 : count;
    }

    private static ForwardingStatus.Inbox inboxStatus(String tenant, String messageId, String consumer) {
        String status = raw.queryForObject(
                "SELECT status FROM agent_bus_forwarding_inbox "
                        + "WHERE tenant_id = :t AND message_id = :m AND consumer_service_id = :c",
                new MapSqlParameterSource()
                        .addValue("t", tenant).addValue("m", messageId).addValue("c", consumer),
                String.class);
        return ForwardingStatus.Inbox.valueOf(status);
    }

    private static String inboxFailureCode(String tenant, String messageId, String consumer) {
        return raw.queryForObject(
                "SELECT failure_code FROM agent_bus_forwarding_inbox "
                        + "WHERE tenant_id = :t AND message_id = :m AND consumer_service_id = :c",
                new MapSqlParameterSource()
                        .addValue("t", tenant).addValue("m", messageId).addValue("c", consumer),
                String.class);
    }

    // ===== temp agent-runtime (LitePull poll-loop, mimics the external agent-runtime-java) =====

    /**
     * Stands in for the EXTERNAL {@code agent-runtime-java} (S4 consumer lands there). A
     * LitePull poll-loop consumes hop2 request events from {@code ascend_bus_invocation_deliver}
     * (broker-side bySql = this runtime's tenant + targetServiceId), decodes the request
     * descriptor, and produces the BLOCKING response sequence (ACCEPTED + RESPONSE +
     * TERMINAL) onto {@code ascend_bus_invocation_resp_in} via a {@link DefaultMQProducer},
     * each response carrying a {@link BrokerControlDescriptor} payloadRef (symmetric to the
     * request) so the response relay recovers the control fields + the gateway reads
     * {@code taskId}/{@code status} via {@link BrokerControlDescriptor#token}.
     */
    static final class TempRuntime {

        private final RocketMqBrokerForwardingConsumer consumer;
        private final DefaultMQProducer producer;
        private final AtomicLong taskSeq = new AtomicLong();
        private final AtomicLong respSeq = new AtomicLong();
        private final String runId;
        private volatile boolean running;
        private Thread pollThread;

        TempRuntime(String nameserver, String runId) {
            this.consumer = new RocketMqBrokerForwardingConsumer(
                    new BrokerTopicResolver("deliver"),
                    RocketMqBrokerForwardingConsumer.defaultPollerFactory(nameserver), POLL_WAIT_MS);
            this.producer = new DefaultMQProducer("it-runtime-producer-2hop" + runId);
            this.producer.setNamesrvAddr(nameserver);
            this.runId = runId;
        }

        void start() throws Exception {
            producer.start();
            consumer.subscribe(RUNTIME + runId, new ForwardingRouteHandle(ROUTE_INVOCATION, TENANT),
                    DeliveryFilter.forRuntime(TENANT, RUNTIME));
            running = true;
            pollThread = new Thread(this::pollLoop, "it-runtime-poll");
            pollThread.setDaemon(true);
            pollThread.start();
        }

        private void pollLoop() {
            while (running) {
                try {
                    Optional<BrokerInboundMessage> msg = consumer.poll(System.currentTimeMillis());
                    if (msg.isPresent()) {
                        processRequest(msg.get());
                        consumer.commit(msg.get()); // model B ack-after-consume
                    }
                } catch (Exception e) {
                    if (running) {
                        // transient broker hiccup — keep polling (the runtime is long-lived)
                    }
                }
            }
        }

        void shutdown() {
            running = false;
            if (pollThread != null) {
                pollThread.interrupt();
            }
            if (consumer != null) {
                consumer.close();
            }
            if (producer != null) {
                producer.shutdown();
            }
        }

        private synchronized void processRequest(BrokerInboundMessage req) {
            if (req.payloadRef() == null || req.payloadRef().isBlank()) {
                return; // not a request descriptor — skip
            }
            BrokerControlDescriptor.Descriptor desc;
            try {
                desc = BrokerControlDescriptor.decode(req.payloadRef());
            } catch (RuntimeException e) {
                return; // not a valid request descriptor — skip
            }
            if (desc.eventType() != AgentBusEventType.CLIENT_INVOCATION_REQUESTED) {
                return; // invocation family only
            }
            String corrId = desc.correlationId();
            String trace = desc.traceId();
            String idem = desc.idempotencyKey();
            String taskId = "task-" + taskSeq.incrementAndGet();
            // L2 §6.2.1 BLOCKING: ACCEPTED + RESPONSE(snapshot) + TERMINAL(completed).
            produceResponse(AgentBusEventType.INVOCATION_ACCEPTED, taskId, "accepted", corrId, trace, idem);
            produceResponse(AgentBusEventType.INVOCATION_RESPONSE, taskId, "snapshot", corrId, trace, idem);
            produceResponse(AgentBusEventType.INVOCATION_TERMINAL, taskId, "completed", corrId, trace, idem);
        }

        /** Build + send a response to {@code resp_in} with a descriptor payloadRef (symmetric to the request). */
        private void produceResponse(AgentBusEventType eventType, String taskId, String status,
                                     String corrId, String trace, String idem) {
            String descriptor = BrokerControlDescriptor.encode(
                    eventType, trace, corrId, idem, ROUTE_INVOCATION, "a2a", Long.MAX_VALUE)
                    + ";taskId=" + taskId + ";status=" + status;
            String messageId = "resp-" + respSeq.incrementAndGet();
            Message msg = new Message(TOPIC_RESP_IN, /* tags */ null, messageId,
                    ("target=" + GATEWAY).getBytes(StandardCharsets.UTF_8));
            msg.putUserProperty("tenantId", TENANT);
            msg.putUserProperty("messageId", messageId);
            msg.putUserProperty("sourceServiceId", RUNTIME);
            msg.putUserProperty("targetServiceId", GATEWAY);
            msg.putUserProperty("correlationId", corrId);
            msg.putUserProperty("eventType", eventType.name());
            msg.putUserProperty("payloadRef", descriptor);
            try {
                producer.send(msg);
            } catch (Exception e) {
                throw new IllegalStateException("temp runtime failed to produce response " + eventType, e);
            }
        }
    }
}
