package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.MapEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq.RocketMqBrokerForwardingConsumer;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq.RocketMqBrokerForwardingRelay;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.InvocationResponseStatus;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerControlDescriptor;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingConsumerPort;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerInboundMessage;
import com.huawei.ascend.bus.forwarding.spi.broker.DeliveryFilter;
import com.huawei.ascend.bus.forwarding.test.InMemoryForwardingOutbox;
import com.huawei.ascend.bus.gateway.runtime.GatewayRuntimeService;
import com.huawei.ascend.bus.spi.ingress.IngressEnvelope;
import com.huawei.ascend.bus.spi.ingress.IngressResponse;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S7 prototype — real-RocketMQ RESPONSE-side integration test for FEAT-013/014
 * (REQ-2026-001 集成联调, env-guarded).
 *
 * <p>Closes the response loop the produce-side IT ({@link RealBrokerProduceSideIntegrationTest})
 * opened. {@link GatewayRuntimeService#dispatchRequest} produces the request onto
 * {@code ascend_bus_invocation_req}; a temp agent-runtime consumer (mimicking
 * {@code TestAgentRuntime}) consumes it and produces its response sequence onto
 * {@code ascend_bus_invocation_resp_out}; a real {@link BrokerForwardingConsumerPort}
 * (LitePull — broker-side bySql filter, model B) drains those responses;
 * {@link GatewayRuntimeService#acceptWindow} polls, classifies, and returns the observable
 * {@link IngressResponse} per L2 feat-013 §6.
 *
 * <p><b>§6 D4 hard proof (slice 3 migration).</b> The response-side + runtime consumers are
 * {@link RocketMqBrokerForwardingConsumer} LitePull adapters (BrokerForwardingConsumerPort) — the
 * test no longer touches {@code DefaultMQPushConsumer} / {@code MessageListenerConcurrently} /
 * {@code MessageExt} (the consumer surface is fully behind the SPI), proving prod encapsulation
 * holds. The gateway consumes via its adapter directly (model B ack-after-consume — a queue between
 * poll and commit would break the ack chain); a separate verifier adapter ({@code it-gateway-verify},
 * different consumer-group) backs {@link #pollNext} / UC-10 so they read the same responses without
 * contending with acceptWindow. The temp runtime runs a LitePull poll-loop (request consumption).
 *
 * <p><b>Blocking poll — why.</b> {@link GatewayRuntimeService#acceptWindow} is a synchronous drain:
 * {@code if (msg == null) break;} exits on the first empty poll. Against a real broker responses
 * arrive asynchronously (Windows gateway → Linux broker → temp runtime → broker → Windows
 * consumer), so the adapter's poll BLOCKS up to {@code pollWaitMs} for each message (the honest
 * representation of a real receiver consumer); a non-blocking drain would return empty before the
 * temp runtime has produced anything → spurious DEFERRED.
 *
 * <p><b>D9 verify.</b> {@link #d9_bysql_filter_is_enforced_broker_side} subscribes a consumer with
 * {@code targetServiceId='nobody'} and produces a message targeted elsewhere — the consumer pulls 0,
 * proving the bySql filter is enforced BROKER-SIDE (not a silent client-side degrade; requires
 * {@code enablePropertyFilter=true}, slice 4).
 *
 * <p><b>Env-guarded.</b> Skipped unless {@code ROCKETMQ_NAMESERVER} is set. Run against a live broker:
 * <pre>{@code
 *   ROCKETMQ_NAMESERVER=7.213.203.4:9876 ../mvnw test -Dtest=RealBrokerResponseSideIntegrationTest
 * }</pre>
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md} §4.2 / §4.3 / §6.2.1 / §6.2.3 / §6.2.4;
 * {@code feat-014-a2a-call-event-forwarding.md} §4.4; decision packet §3 / §6 D4 / D9 / D14.
 */
// scope: forwarding transport.broker test — real-RocketMQ response-side IT (env-guarded, S7 prototype)
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_NAMESERVER", matches = ".+")
@Isolated
class RealBrokerResponseSideIntegrationTest {

    private static final String TENANT = "tenant-a";
    private static final String GATEWAY = "it-gateway";
    private static final String RUNTIME = "it-agent-runtime";
    // distinct consumer-GROUPS so A (acceptWindow) and V (verifier) each receive every response
    // (same group would load-balance queues → each misses what the other got).
    private static final String GROUP_RESP = "it-gateway-resp";
    private static final String GROUP_VERIFY = "it-gateway-verify";
    private static final String TRACE = "0123456789abcdef0123456789abcdef";
    private static final String ROUTE_INVOCATION = "route-invocation";
    private static final String ROUTE_A2A = "route-a2a";
    private static final String TOPIC_INVOCATION_REQ = "ascend_bus_invocation_req";
    private static final String TOPIC_A2A_REQ = "ascend_bus_a2a_req";
    private static final String TOPIC_INVOCATION_RESP_OUT = "ascend_bus_invocation_resp_out";
    private static final String TOPIC_A2A_RESP_OUT = "ascend_bus_a2a_resp_out";

    private static final long ACCEPT_TIMEOUT_MS = 15_000L;
    private static final long RESPONSE_TIMEOUT_MS = 30_000L;
    // the adapter's poll blocks up to this long for an async message (broker round-trip latency budget).
    private static final long POLL_WAIT_MS = 5_000L;

    // produce-side (gateway dispatch) + the three LitePull consumer adapters (A=gateway resp, V=verifier, T=temp runtime req).
    private static DefaultMQProducer gatewayProducer;
    private static RocketMqBrokerForwardingConsumer responseAdapter;   // A: acceptWindow polls this (model B)
    private static RocketMqBrokerForwardingConsumer verifyAdapter;     // V: pollNext / UC-10 (separate group → all messages)
    private static TempRuntime tempRuntime;                           // T: LitePull poll-loop consuming requests

    private GatewayRuntimeService gateway;

    @BeforeAll
    static void startBrokerClients() throws Exception {
        String nameserver = System.getenv("ROCKETMQ_NAMESERVER");

        gatewayProducer = new DefaultMQProducer("it-gateway-producer");
        gatewayProducer.setNamesrvAddr(nameserver);
        gatewayProducer.start();

        ForwardingEndpointResolver respResolver = new MapEndpointResolver(Map.of(
                TOPIC_INVOCATION_RESP_OUT, TOPIC_INVOCATION_RESP_OUT,
                TOPIC_A2A_RESP_OUT, TOPIC_A2A_RESP_OUT));
        DeliveryFilter respFilter = DeliveryFilter.forRuntime(TENANT, GATEWAY);

        // A — the gateway's response consumer (acceptWindow drains this directly; model B commit).
        responseAdapter = new RocketMqBrokerForwardingConsumer(
                respResolver, RocketMqBrokerForwardingConsumer.defaultPollerFactory(nameserver), POLL_WAIT_MS);
        responseAdapter.subscribe(GROUP_RESP, new ForwardingRouteHandle(TOPIC_INVOCATION_RESP_OUT, TENANT), respFilter);
        responseAdapter.subscribe(GROUP_RESP, new ForwardingRouteHandle(TOPIC_A2A_RESP_OUT, TENANT), respFilter);

        // V — verifier (different consumer-group → receives every response, independently of A's drain).
        verifyAdapter = new RocketMqBrokerForwardingConsumer(
                respResolver, RocketMqBrokerForwardingConsumer.defaultPollerFactory(nameserver), POLL_WAIT_MS);
        verifyAdapter.subscribe(GROUP_VERIFY, new ForwardingRouteHandle(TOPIC_INVOCATION_RESP_OUT, TENANT), respFilter);
        verifyAdapter.subscribe(GROUP_VERIFY, new ForwardingRouteHandle(TOPIC_A2A_RESP_OUT, TENANT), respFilter);

        tempRuntime = new TempRuntime(nameserver);
        tempRuntime.start();

        // CONSUME_FROM_LAST_OFFSET: a consumer only sees messages produced AFTER it is assigned queues;
        // let rebalance settle (A + V + T) before any test dispatches.
        Thread.sleep(3_000L);
    }

    @AfterAll
    static void shutdownBrokerClients() {
        if (tempRuntime != null) {
            tempRuntime.shutdown();
        }
        if (responseAdapter != null) {
            responseAdapter.close();
        }
        if (verifyAdapter != null) {
            verifyAdapter.close();
        }
        if (gatewayProducer != null) {
            gatewayProducer.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        tempRuntime.setResponseMode(TempRuntime.ResponseMode.BLOCKING);
        wireGateway(ACCEPT_TIMEOUT_MS, RESPONSE_TIMEOUT_MS);
    }

    /** Wire the gateway with per-test accept/response timeouts; the response consumer is the shared adapter A. */
    private void wireGateway(long acceptTimeoutMs, long responseTimeoutMs) {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        MapEndpointResolver resolver = new MapEndpointResolver(Map.of(
                ROUTE_INVOCATION, TOPIC_INVOCATION_REQ,
                ROUTE_A2A, TOPIC_A2A_REQ));
        RocketMqBrokerForwardingRelay relay = new RocketMqBrokerForwardingRelay(
                resolver, RocketMqBrokerForwardingRelay.defaultSender(gatewayProducer));
        gateway = new GatewayRuntimeService(outbox, outbox, relay, responseAdapter,
                GATEWAY, acceptTimeoutMs, responseTimeoutMs, System::currentTimeMillis);
    }

    /**
     * Drain the verifier (V) for the next response whose {@code correlationId} matches
     * {@code expectedCorrId} (blocking). Polling by corrId disambiguates the two TERMINAL variants
     * and skips leftovers from prior tests — V (a separate group) accumulates every response, so
     * non-matching ones are committed + skipped here.
     */
    private BrokerInboundMessage pollNext(String expectedCorrId) {
        long deadline = System.currentTimeMillis() + ACCEPT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            BrokerInboundMessage msg = verifyAdapter.poll(System.currentTimeMillis()).orElse(null);
            if (msg == null) {
                break; // V's poll timed out (POLL_WAIT_MS) with no message
            }
            if (expectedCorrId.equals(msg.correlationId())) {
                return msg;
            }
            verifyAdapter.commit(msg); // leftover / different corrId — advance V and keep polling
        }
        throw new AssertionError("no response with correlationId=" + expectedCorrId
                + " polled within " + ACCEPT_TIMEOUT_MS + "ms");
    }

    private IngressEnvelope runCreate(UUID requestId) {
        return new IngressEnvelope(
                requestId, TENANT, UUID.randomUUID(),
                IngressEnvelope.IngressRequestType.RUN_CREATE,
                "payload-body", TRACE, /* deadlineMillisEpoch */ null,
                Map.of("routeHandle", ROUTE_INVOCATION, "targetServiceId", RUNTIME));
    }

    /**
     * UC-4 — blocking call (L2 §6.2.1). The temp runtime consumes the dispatched RUN_CREATE
     * and produces BLOCKING (ACCEPTED + RESPONSE + TERMINAL(completed)). acceptWindow records
     * the ACCEPTED taskId, then classifies the RESPONSE as COMPLETED_RESPONSE →
     * {@link IngressResponse#accepted} with {@code cursor=taskId}.
     */
    @Test
    void uc4_blocking_call_returns_completed_accepted() {
        UUID requestId = UUID.randomUUID();
        gateway.dispatchRequest(runCreate(requestId));
        IngressResponse resp = gateway.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).startsWith("task-");
        assertThat(resp.rejectionReason()).isNull();
    }

    /**
     * UC-5 — timeout/degraded (L2 §6.2.3). The temp runtime is SILENT (creates task, emits
     * nothing); acceptWindow polls up to the (short) accept window, drains empty → UNKNOWN →
     * {@link IngressResponse#deferred}. A short acceptTimeoutMs keeps the test fast.
     */
    @Test
    void uc5_silent_timeout_returns_deferred() {
        tempRuntime.setResponseMode(TempRuntime.ResponseMode.SILENT);
        wireGateway(2_000L, 5_000L);
        UUID requestId = UUID.randomUUID();
        gateway.dispatchRequest(runCreate(requestId));
        IngressResponse resp = gateway.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.DEFERRED);
        assertThat(resp.cursor()).isNull();
        assertThat(resp.rejectionReason()).isNull();
    }

    /**
     * UC-6 — classify each response eventType from a real broker-surfaced message. The
     * eventType user-property must survive the broker round-trip (produce → LitePull consume →
     * {@link BrokerInboundMessage}); {@link GatewayRuntimeService#classify} then maps it.
     */
    @Test
    void uc6_classify_each_event_type_from_real_broker() {
        assertClassify(AgentBusEventType.INVOCATION_ACCEPTED, "taskId=t1",
                InvocationResponseStatus.ACCEPTED_WITH_TASK);
        assertClassify(AgentBusEventType.INVOCATION_RESPONSE, "taskId=t2;status=snapshot",
                InvocationResponseStatus.COMPLETED_RESPONSE);
        assertClassify(AgentBusEventType.INVOCATION_STREAM_READY, "taskId=t3;streamRef=stream://t3",
                InvocationResponseStatus.STREAM_READY);
        assertClassify(AgentBusEventType.INVOCATION_REJECTED, "reason=denied",
                InvocationResponseStatus.REJECTED);
        assertClassify(AgentBusEventType.INVOCATION_FAILED, "reason=boom",
                InvocationResponseStatus.FAILED);
        assertClassify(AgentBusEventType.INVOCATION_TERMINAL, "taskId=t4;status=completed",
                InvocationResponseStatus.COMPLETED_RESPONSE);
        assertClassify(AgentBusEventType.INVOCATION_TERMINAL, "taskId=t5;status=failed",
                InvocationResponseStatus.FAILED);
    }

    /** Produce one response of {@code eventType}, poll it by corrId, assert classify maps it. */
    private void assertClassify(AgentBusEventType eventType, String payloadRef,
                                InvocationResponseStatus expected) {
        String corrId = UUID.randomUUID().toString();
        tempRuntime.produceResponse(eventType, payloadRef, corrId);
        assertThat(gateway.classify(pollNext(corrId))).isEqualTo(expected);
    }

    /**
     * UC-7 — streaming (L2 §6.2.4). The temp runtime is STREAMING (ACCEPTED + STREAM_READY);
     * acceptWindow records the ACCEPTED taskId, then classifies STREAM_READY →
     * {@link IngressResponse#accepted} with {@code cursor=streamRef}.
     */
    @Test
    void uc7_streaming_returns_accepted_with_stream_ref_cursor() {
        tempRuntime.setResponseMode(TempRuntime.ResponseMode.STREAMING);
        UUID requestId = UUID.randomUUID();
        gateway.dispatchRequest(runCreate(requestId));
        IngressResponse resp = gateway.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).startsWith("stream://");
    }

    /**
     * UC-8 — skip-own + non-matching corrId. A self-source response (sourceServiceId=GATEWAY,
     * matching corrId) must be skipped by acceptWindow's self-consumption guard; a non-matching
     * corrId response must be skipped by the corrId filter. With neither matched, the window
     * drains empty → DEFERRED.
     */
    @Test
    void uc8_skips_self_source_and_non_matching_corr_id() {
        wireGateway(3_000L, 5_000L);
        UUID requestId = UUID.randomUUID();
        String matched = requestId.toString();
        // self-source (matching corrId, source=GATEWAY) → skip-own
        tempRuntime.produceResponse(AgentBusEventType.INVOCATION_RESPONSE,
                "taskId=self-1;status=snapshot", matched, TENANT, GATEWAY, GATEWAY);
        // non-matching corrId → corrId-filter skip
        tempRuntime.produceResponse(AgentBusEventType.INVOCATION_RESPONSE,
                "taskId=other-1;status=snapshot", UUID.randomUUID().toString());

        IngressResponse resp = gateway.acceptWindow(requestId, TENANT);
        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.DEFERRED);
        assertThat(resp.cursor()).isNull();
    }

    /**
     * UC-9 — tenant isolation. A cross-tenant RESPONSE (tenant-b, matching corrId) must be
     * filtered by acceptWindow's client-side tenant guard (D13) — committed + skipped, never
     * returned. With nothing matched, the window drains empty → DEFERRED.
     */
    @Test
    void uc9_tenant_isolation_filters_cross_tenant() {
        wireGateway(3_000L, 5_000L);
        UUID requestId = UUID.randomUUID();
        String matched = requestId.toString();
        // cross-tenant (tenant-b) RESPONSE with MATCHING corrId → filtered by acceptWindow's tenant guard
        tempRuntime.produceResponse(AgentBusEventType.INVOCATION_RESPONSE,
                "taskId=B-1;status=snapshot", matched, "tenant-b", RUNTIME, GATEWAY);

        IngressResponse resp = gateway.acceptWindow(requestId, TENANT);
        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.DEFERRED);
        assertThat(resp.cursor()).isNull();
    }

    /**
     * UC-10 — idempotency (L2 §4.4). Two dispatches with the same (tenantId, idempotencyKey)
     * → the temp runtime dedups (tenantId|idempotencyKey) → both responses share ONE taskId.
     * Drain the verifier for all responses matching this corrId and assert exactly one distinct
     * taskId.
     */
    @Test
    void uc10_idempotency_duplicate_dispatch_emits_one_task_id() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        String corrId = requestId.toString();
        IngressEnvelope env = new IngressEnvelope(requestId, TENANT, idempotencyKey,
                IngressEnvelope.IngressRequestType.RUN_CREATE, "payload-body", TRACE, null,
                Map.of("routeHandle", ROUTE_INVOCATION, "targetServiceId", RUNTIME));
        gateway.dispatchRequest(env);
        gateway.dispatchRequest(env); // same (tenantId, idempotencyKey) → server-side dedup
        // let the temp runtime consume both + produce responses (cross-machine)
        Thread.sleep(4_000L);
        java.util.Set<String> taskIds = new java.util.HashSet<>();
        long drainDeadline = System.currentTimeMillis() + 6_000L;
        while (System.currentTimeMillis() < drainDeadline) {
            BrokerInboundMessage m = verifyAdapter.poll(System.currentTimeMillis()).orElse(null);
            if (m == null) {
                break;
            }
            if (corrId.equals(m.correlationId())) {
                String tid = BrokerControlDescriptor.token(m.payloadRef(), "taskId");
                if (tid != null) {
                    taskIds.add(tid);
                }
            }
            verifyAdapter.commit(m);
        }
        assertThat(taskIds).hasSize(1);
    }

    /**
     * D9 — bySql filter is enforced BROKER-SIDE (not a silent client-side degrade; requires
     * {@code enablePropertyFilter=true}, slice 4). RIGOROUS (avoids the false-positive where pulling 0
     * is ambiguous between "broker filtered" and "consumer never received"): first a MATCHING message
     * ({@code targetServiceId='nobody'}) MUST be delivered — proving the consumer is subscribed + bySql
     * matches — then a NON-MATCHING one ({@code targetServiceId=RUNTIME}) MUST be filtered broker-side
     * (pull 0). If {@code enablePropertyFilter} were off, the broker would deliver the non-matching
     * message (the adapter does NOT client-filter — {@code supportsBrokerSidePropertyFilter()=true}),
     * surfacing the missing config as a failure rather than a silent degrade.
     */
    @Test
    void d9_bysql_filter_is_enforced_broker_side() throws Exception {
        String nameserver = System.getenv("ROCKETMQ_NAMESERVER");
        ForwardingEndpointResolver respResolver = new MapEndpointResolver(
                Map.of(TOPIC_INVOCATION_RESP_OUT, TOPIC_INVOCATION_RESP_OUT));
        RocketMqBrokerForwardingConsumer nobody = new RocketMqBrokerForwardingConsumer(
                respResolver, RocketMqBrokerForwardingConsumer.defaultPollerFactory(nameserver), POLL_WAIT_MS);
        nobody.subscribe("it-nobody", new ForwardingRouteHandle(TOPIC_INVOCATION_RESP_OUT, TENANT),
                new DeliveryFilter(Map.of("targetServiceId", "nobody")));
        assertThat(nobody.supportsBrokerSidePropertyFilter()).isTrue();
        Thread.sleep(2_000L); // let rebalance settle before producing

        DefaultMQProducer producer = new DefaultMQProducer("it-d9-producer");
        producer.setNamesrvAddr(nameserver);
        producer.start();
        try {
            // 1) MATCHING: targetServiceId='nobody' → bySql matches → the broker MUST deliver it. This
            //    rules out "consumer not receiving" (subscribe/rebalance issue) as the cause of a zero pull.
            sendD9Message(producer, "resp-d9-match", "nobody");
            Optional<BrokerInboundMessage> matched = pollFirst(nobody, 6_000L);
            assertThat(matched).as("matching targetServiceId='nobody' message must be delivered").isPresent();
            nobody.commit(matched.orElseThrow());

            // 2) NON-MATCHING: targetServiceId=RUNTIME (≠ 'nobody') → the broker filters broker-side → pull 0.
            sendD9Message(producer, "resp-d9-nomatch", RUNTIME);
            assertThat(pollFirst(nobody, 4_000L))
                    .as("non-matching (target=RUNTIME) message must be filtered broker-side; receiving it "
                            + "means enablePropertyFilter is off (bySql silently degraded to client-deliver)")
                    .isEmpty();
        } finally {
            producer.shutdown();
            nobody.close();
        }
    }

    /** Produce one response message to the resp topic with the given targetServiceId (used by D9). */
    private static void sendD9Message(DefaultMQProducer producer, String messageId, String targetServiceId) throws Exception {
        Message msg = new Message(TOPIC_INVOCATION_RESP_OUT, /* tags */ null, messageId,
                ("target=" + targetServiceId).getBytes(StandardCharsets.UTF_8));
        msg.putUserProperty("tenantId", TENANT);
        msg.putUserProperty("messageId", messageId);
        msg.putUserProperty("sourceServiceId", RUNTIME);
        msg.putUserProperty("targetServiceId", targetServiceId);
        msg.putUserProperty("correlationId", UUID.randomUUID().toString());
        msg.putUserProperty("eventType", AgentBusEventType.INVOCATION_RESPONSE.name());
        msg.putUserProperty("payloadRef", "taskId=d9;status=snapshot");
        assertThat(producer.send(msg).getSendStatus()).isEqualTo(SendStatus.SEND_OK);
    }

    /** Poll a consumer until it returns a message or the timeout elapses (empty → no matching message). */
    private static Optional<BrokerInboundMessage> pollFirst(RocketMqBrokerForwardingConsumer c, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Optional<BrokerInboundMessage> m = c.poll(System.currentTimeMillis());
            if (m.isPresent()) {
                return m;
            }
        }
        return Optional.empty();
    }


    // ===== temp agent-runtime (LitePull poll-loop, mimics TestAgentRuntime against the real broker) =====

    /**
     * Stands in for the EXTERNAL {@code agent-runtime-java} (S4 consumer lands there). A LitePull
     * poll-loop consumes request events from {@code ascend_bus_invocation_req} (broker-side bySql
     * filter = this runtime's tenant + targetServiceId), "processes" per the L2 §4.3 response state
     * machine, and produces response events back onto {@code ascend_bus_invocation_resp_out} via a
     * {@link DefaultMQProducer}, mirroring {@link RocketMqBrokerForwardingRelay#buildMessage} so the
     * response adapters read the same routing user-properties. §6 D4: request consumption is via the
     * adapter (no DefaultMQPushConsumer / MessageListener / MessageExt here).
     */
    static final class TempRuntime {

        /** Configurable response behaviour for a consumed REQUESTED event (mirrors TestAgentRuntime). */
        enum ResponseMode { BLOCKING, SILENT, STREAMING }

        private final RocketMqBrokerForwardingConsumer consumer;   // T: LitePull adapter (request consumption)
        private final DefaultMQProducer producer;                  // produce responses
        private final AtomicLong taskSeq = new AtomicLong();
        private final AtomicLong respSeq = new AtomicLong();
        private final Map<String, TaskEntry> taskByKey = new ConcurrentHashMap<>();
        private volatile ResponseMode responseMode = ResponseMode.BLOCKING;
        private volatile boolean running;
        private Thread pollThread;

        TempRuntime(String nameserver) {
            ForwardingEndpointResolver reqResolver = new MapEndpointResolver(
                    Map.of(ROUTE_INVOCATION, TOPIC_INVOCATION_REQ, ROUTE_A2A, TOPIC_A2A_REQ));
            this.consumer = new RocketMqBrokerForwardingConsumer(
                    reqResolver, RocketMqBrokerForwardingConsumer.defaultPollerFactory(nameserver), POLL_WAIT_MS);
            this.producer = new DefaultMQProducer("it-temp-runtime-producer");
            producer.setNamesrvAddr(nameserver);
        }

        void start() throws Exception {
            producer.start();
            consumer.subscribe(RUNTIME, new ForwardingRouteHandle(ROUTE_INVOCATION, TENANT),
                    DeliveryFilter.forRuntime(TENANT, RUNTIME));
            running = true;
            pollThread = new Thread(this::pollLoop, "it-temp-runtime-poll");
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

        void setResponseMode(ResponseMode mode) {
            this.responseMode = mode;
        }

        /** A created task + whether its response sequence was already emitted (§4.4 layer 2). */
        private static final class TaskEntry {
            final String taskId;
            volatile boolean emitted;

            TaskEntry(String taskId) {
                this.taskId = taskId;
            }
        }

        private synchronized void processRequest(BrokerInboundMessage req) {
            String payloadRef = req.payloadRef();
            if (payloadRef == null || payloadRef.isBlank()) {
                return;
            }
            BrokerControlDescriptor.Descriptor desc;
            try {
                desc = BrokerControlDescriptor.decode(payloadRef);
            } catch (IllegalArgumentException e) {
                return; // not a request descriptor (own response echo) — skip
            }
            if (desc.eventType() != AgentBusEventType.CLIENT_INVOCATION_REQUESTED) {
                return; // UC-4..UC-10 scope: invocation family only
            }
            String reqTenant = req.tenantId();
            String reqSource = req.sourceServiceId(); // = GATEWAY (response target)
            String corrId = desc.correlationId();
            // §4.4 server-side creation idempotency: (tenantId|idempotencyKey) → single task.
            String dedupKey = reqTenant + "|" + desc.idempotencyKey();
            TaskEntry entry = taskByKey.computeIfAbsent(dedupKey,
                    k -> new TaskEntry("task-" + taskSeq.incrementAndGet()));
            ResponseMode mode = this.responseMode;
            if (mode == ResponseMode.SILENT) {
                return; // task created, nothing emitted → gateway accept window times out (UC-5)
            }
            String taskId = entry.taskId;
            if (entry.emitted) {
                // §4.4 repeat REQUESTED → re-emit only ACCEPTED (same taskId, no second logical call)
                produceResponse(AgentBusEventType.INVOCATION_ACCEPTED, "taskId=" + taskId, corrId,
                        reqTenant, RUNTIME, reqSource);
                return;
            }
            entry.emitted = true;
            // L2 §6.2.1 BLOCKING: ACCEPTED + RESPONSE + TERMINAL(completed).
            produceResponse(AgentBusEventType.INVOCATION_ACCEPTED, "taskId=" + taskId, corrId,
                    reqTenant, RUNTIME, reqSource);
            if (mode == ResponseMode.STREAMING) {
                // L2 §6.2.4 STREAMING: ACCEPTED + STREAM_READY (cursor=streamRef).
                produceResponse(AgentBusEventType.INVOCATION_STREAM_READY,
                        "taskId=" + taskId + ";streamRef=stream://" + taskId, corrId,
                        reqTenant, RUNTIME, reqSource);
                return;
            }
            produceResponse(AgentBusEventType.INVOCATION_RESPONSE, "taskId=" + taskId + ";status=snapshot",
                    corrId, reqTenant, RUNTIME, reqSource);
            produceResponse(AgentBusEventType.INVOCATION_TERMINAL, "taskId=" + taskId + ";status=completed",
                    corrId, reqTenant, RUNTIME, reqSource);
        }

        /**
         * Build + send a single response message to {@code resp_out} mirroring
         * {@link RocketMqBrokerForwardingRelay#buildMessage} user-properties. Package-private so
         * UC-6 (per-eventType classify) + D9 can inject a chosen eventType directly; {@code source=RUNTIME},
         * {@code target=GATEWAY} (the response swap).
         */
        void produceResponse(AgentBusEventType eventType, String respPayloadRef, String correlationId,
                             String tenantId, String source, String target) {
            String messageId = "resp-" + respSeq.incrementAndGet();
            Message msg = new Message(TOPIC_INVOCATION_RESP_OUT, /* tags */ null, messageId,
                    ("target=" + target).getBytes(StandardCharsets.UTF_8));
            msg.putUserProperty("tenantId", tenantId);
            msg.putUserProperty("messageId", messageId);
            msg.putUserProperty("sourceServiceId", source);
            msg.putUserProperty("targetServiceId", target);
            msg.putUserProperty("correlationId", correlationId);
            msg.putUserProperty("eventType", eventType.name());
            msg.putUserProperty("payloadRef", respPayloadRef);
            try {
                producer.send(msg);
            } catch (Exception e) {
                throw new IllegalStateException("temp runtime failed to produce response " + eventType, e);
            }
        }

        /** UC-6 / UC-8 / UC-9 / D9 direct-injection overload: response-swap defaults (source=RUNTIME, target=GATEWAY, tenant=TENANT). */
        void produceResponse(AgentBusEventType eventType, String respPayloadRef, String correlationId) {
            produceResponse(eventType, respPayloadRef, correlationId, TENANT, RUNTIME, GATEWAY);
        }
    }
}
