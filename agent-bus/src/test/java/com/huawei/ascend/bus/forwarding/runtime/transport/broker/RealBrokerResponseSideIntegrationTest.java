package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import com.huawei.ascend.bus.forwarding.runtime.transport.MapEndpointResolver;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.InvocationResponseStatus;
import com.huawei.ascend.bus.forwarding.test.InMemoryForwardingOutbox;
import com.huawei.ascend.bus.gateway.runtime.GatewayRuntimeService;
import com.huawei.ascend.bus.spi.ingress.IngressEnvelope;
import com.huawei.ascend.bus.spi.ingress.IngressResponse;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S7 prototype — real-RocketMQ RESPONSE-side integration test for FEAT-013/014
 * (REQ-2026-001 集成联调, env-guarded).
 *
 * <p>Closes the response loop the produce-side IT ({@link RealBrokerProduceSideIntegrationTest})
 * opened. {@link GatewayRuntimeService#dispatchRequest} produces the request onto
 * {@code ascend_bus_invocation_req}; a temp agent-runtime consumer (mimicking
 * {@link com.huawei.ascend.bus.test.TestAgentRuntime}) consumes it and produces its
 * response sequence onto {@code ascend_bus_invocation_resp_out}; a real
 * {@link BrokerForwardingConsumerPort} (push consumer → blocking poll) drains those
 * responses; {@link GatewayRuntimeService#acceptWindow} polls, classifies, and returns
 * the observable {@link IngressResponse} per L2 feat-013 §6.
 *
 * <p><b>Blocking poll — why.</b> {@link GatewayRuntimeService#acceptWindow} is a
 * synchronous drain: {@code if (msg == null) break;} exits on the first empty poll.
 * The unit tests pre-queue responses into a {@code FakeConsumer} before calling
 * acceptWindow (synchronous {@link InMemoryBroker}). Against a real broker the responses
 * arrive asynchronously (Windows gateway → Linux broker → temp runtime → Linux broker
 * → Windows consumer), so a non-blocking drain would return empty before the temp
 * runtime has produced anything → spurious DEFERRED. The test-local
 * {@link ResponseSideConsumer#poll} therefore blocks up to {@code pollWaitMs} for each
 * message (bounded by the gateway's accept window), returning empty only when the
 * window truly expires — the honest representation of a real receiver consumer.
 *
 * <p><b>Env-guarded.</b> Skipped unless {@code ROCKETMQ_NAMESERVER} is set. Run against a
 * live broker with:
 * <pre>{@code
 *   ROCKETMQ_NAMESERVER=7.213.203.4:9876 ../mvnw test -Dtest=RealBrokerResponseSideIntegrationTest
 * }</pre>
 * JUnit-native {@link EnabledIfEnvironmentVariable} (NOT Spring's
 * {@code @EnabledIfEnvironmentProperty}) — agent-bus has no {@code @SpringBootTest}, so
 * the Spring annotation is never evaluated. Same env-guard intent, framework-independent.
 *
 * <p><b>UC coverage.</b> UC-4 (blocking → COMPLETED_RESPONSE → {@code accepted(cursor=taskId)}),
 * UC-5 (silent timeout → DEFERRED), UC-6 (per-eventType classify on real broker-surfaced
 * messages), UC-7 (streaming → {@code accepted(cursor=streamRef)}). UC-8..UC-10 added next.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md} §4.2 / §4.3 / §6.2.1 / §6.2.3 / §6.2.4;
 * {@code feat-014-a2a-call-event-forwarding.md} §4.4.
 */
// scope: forwarding transport.broker test — real-RocketMQ response-side IT (env-guarded, S7 prototype)
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_NAMESERVER", matches = ".+")
@Isolated
class RealBrokerResponseSideIntegrationTest {

    private static final String TENANT = "tenant-a";
    private static final String GATEWAY = "it-gateway";
    private static final String RUNTIME = "it-agent-runtime";
    private static final String TRACE = "0123456789abcdef0123456789abcdef";
    private static final String ROUTE_INVOCATION = "route-invocation";
    private static final String ROUTE_A2A = "route-a2a";
    // RocketMQ topic-name validator (^[%|a-zA-Z0-9_-]+$) forbids '.' — see
    // RealBrokerProduceSideIntegrationTest for the dotted→underscore reconciliation.
    private static final String TOPIC_INVOCATION_REQ = "ascend_bus_invocation_req";
    private static final String TOPIC_A2A_REQ = "ascend_bus_a2a_req";
    private static final String TOPIC_INVOCATION_RESP_OUT = "ascend_bus_invocation_resp_out";
    private static final String TOPIC_A2A_RESP_OUT = "ascend_bus_a2a_resp_out";

    private static final long ACCEPT_TIMEOUT_MS = 15_000L;
    private static final long RESPONSE_TIMEOUT_MS = 30_000L;

    private static DefaultMQProducer gatewayProducer;
    private static DefaultMQPushConsumer responseConsumerPush;
    private static final LinkedBlockingQueue<MessageExt> responseQueue = new LinkedBlockingQueue<>();
    private static TempRuntime tempRuntime;

    private GatewayRuntimeService gateway;

    @BeforeAll
    static void startBrokerClients() throws Exception {
        String nameserver = System.getenv("ROCKETMQ_NAMESERVER");

        gatewayProducer = new DefaultMQProducer("it-gateway-producer");
        gatewayProducer.setNamesrvAddr(nameserver);
        gatewayProducer.start();

        // responseConsumerPush feeds responseQueue; acceptWindow drains via the
        // ResponseSideConsumer port (blocking poll + L2 header-tenant-check filter).
        responseConsumerPush = new DefaultMQPushConsumer("it-gateway-consumer");
        responseConsumerPush.setNamesrvAddr(nameserver);
        responseConsumerPush.subscribe(TOPIC_INVOCATION_RESP_OUT, "*");
        responseConsumerPush.subscribe(TOPIC_A2A_RESP_OUT, "*");
        responseConsumerPush.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            responseQueue.addAll(msgs);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        responseConsumerPush.start();

        tempRuntime = new TempRuntime(nameserver);
        tempRuntime.start();

        // Default CONSUME_FROM_LAST_OFFSET consumes only messages produced AFTER the
        // consumer is assigned queues; let rebalance settle before dispatching.
        Thread.sleep(3_000L);
    }

    @AfterAll
    static void shutdownBrokerClients() {
        if (tempRuntime != null) {
            tempRuntime.shutdown();
        }
        if (responseConsumerPush != null) {
            responseConsumerPush.shutdown();
        }
        if (gatewayProducer != null) {
            gatewayProducer.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        responseQueue.clear();
        tempRuntime.setResponseMode(TempRuntime.ResponseMode.BLOCKING);
        wireGateway(ACCEPT_TIMEOUT_MS, RESPONSE_TIMEOUT_MS);
    }

    /** Wire the gateway with per-test accept/response timeouts; pollWaitMs tracks acceptTimeoutMs. */
    private void wireGateway(long acceptTimeoutMs, long responseTimeoutMs) {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        MapEndpointResolver resolver = new MapEndpointResolver(Map.of(
                ROUTE_INVOCATION, TOPIC_INVOCATION_REQ,
                ROUTE_A2A, TOPIC_A2A_REQ));
        RocketMqBrokerForwardingRelay relay = new RocketMqBrokerForwardingRelay(
                resolver, RocketMqBrokerForwardingRelay.defaultSender(gatewayProducer));
        BrokerForwardingConsumerPort responseConsumer =
                new ResponseSideConsumer(responseQueue, acceptTimeoutMs);
        responseConsumer.subscribe(GATEWAY, new ForwardingRouteHandle("gateway-" + GATEWAY, TENANT),
                DeliveryFilter.forRuntime(TENANT, GATEWAY));
        gateway = new GatewayRuntimeService(outbox, outbox, relay, responseConsumer,
                GATEWAY, acceptTimeoutMs, responseTimeoutMs, System::currentTimeMillis);
    }

    /**
     * Drain the response queue for the next response whose {@code correlationId} matches
     * {@code expectedCorrId} (blocking). Polling by corrId (not just eventType) disambiguates
     * the two TERMINAL variants (completed vs failed) and skips any leftover response from a
     * prior test (e.g. UC-4's terminal, not consumed because acceptWindow returns on
     * COMPLETED_RESPONSE) — keeps UC-6 robust to cross-test delivery races.
     */
    private BrokerInboundMessage pollNext(String expectedCorrId) {
        ResponseSideConsumer consumer = new ResponseSideConsumer(responseQueue, ACCEPT_TIMEOUT_MS);
        consumer.subscribe(GATEWAY, new ForwardingRouteHandle("gateway-" + GATEWAY, TENANT),
                DeliveryFilter.forRuntime(TENANT, GATEWAY));
        long deadline = System.currentTimeMillis() + ACCEPT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            BrokerInboundMessage msg = consumer.poll(System.currentTimeMillis())
                    .orElse(null);
            if (msg == null) {
                break;
            }
            if (expectedCorrId.equals(msg.correlationId())) {
                return msg;
            }
            // leftover from a prior test (different corrId) — discarded; keep polling for ours.
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
     * eventType user-property must survive the broker round-trip (produce → push-consume →
     * {@link BrokerInboundMessage}); {@link GatewayRuntimeService#classify} then maps it.
     * Invocation family here (A2A twins are symmetric and unit-pinned in GatewayRuntimeServiceTest).
     */
    @Test
    void uc6_classify_each_event_type_from_real_broker() {
        // Each produced response carries a unique corrId; pollNext polls by corrId so the two
        // TERMINAL variants (completed vs failed) are disambiguated and leftovers are skipped.
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
     * drains empty → DEFERRED. (If either skip is broken, that message matches → ACCEPTED → fail.)
     * Order-independent: both messages are skipped regardless of delivery order.
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
     * filtered by the responseConsumer's header-tenant-check (L2 §6.2 ⑤) — never returned to
     * acceptWindow. With nothing matched, the window drains empty → DEFERRED. (If the filter
     * is broken, the tenant-b response matches → ACCEPTED → fail.)
     */
    @Test
    void uc9_tenant_isolation_filters_cross_tenant() {
        wireGateway(3_000L, 5_000L);
        UUID requestId = UUID.randomUUID();
        String matched = requestId.toString();
        // cross-tenant (tenant-b) RESPONSE with MATCHING corrId → filtered by poll's tenant guard
        tempRuntime.produceResponse(AgentBusEventType.INVOCATION_RESPONSE,
                "taskId=B-1;status=snapshot", matched, "tenant-b", RUNTIME, GATEWAY);

        IngressResponse resp = gateway.acceptWindow(requestId, TENANT);
        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.DEFERRED);
        assertThat(resp.cursor()).isNull();
    }

    /**
     * UC-10 — idempotency (L2 §4.4). Two dispatches with the same (tenantId, idempotencyKey)
     * → the temp runtime dedups (tenantId|idempotencyKey) → both responses share ONE taskId;
     * a broken dedup would surface a 2nd distinct taskId. Drain all responses matching this
     * corrId and assert exactly one distinct taskId. (Cross-test leftovers carry other corrIds
     * and are skipped, so this is contamination-proof — unlike a global taskCount.)
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
        ResponseSideConsumer drainer = new ResponseSideConsumer(responseQueue, 2_000L);
        drainer.subscribe(GATEWAY, new ForwardingRouteHandle("gateway-" + GATEWAY, TENANT),
                DeliveryFilter.forRuntime(TENANT, GATEWAY));
        long drainDeadline = System.currentTimeMillis() + 6_000L;
        while (System.currentTimeMillis() < drainDeadline) {
            BrokerInboundMessage m = drainer.poll(System.currentTimeMillis()).orElse(null);
            if (m == null) {
                break;
            }
            if (corrId.equals(m.correlationId())) {
                String tid = BrokerControlDescriptor.token(m.payloadRef(), "taskId");
                if (tid != null) {
                    taskIds.add(tid);
                }
            }
        }
        assertThat(taskIds).hasSize(1);
    }

    // ===== test-local BrokerForwardingConsumerPort: push consumer → blocking poll =====

    /**
     * Real {@link BrokerForwardingConsumerPort} over a {@link DefaultMQPushConsumer}. The push
     * consumer's listener enqueues {@link MessageExt}s into a shared
     * {@link LinkedBlockingQueue}; {@link #poll} blocks up to {@code pollWaitMs} for the next
     * message, filters by header {@code tenantId} (L2 cross-tenant defense — a mismatched-tenant
     * message is skipped, never returned), and mirrors the surviving routing headers onto a
     * {@link BrokerInboundMessage} (with the polling {@code consumerServiceId} materialised at
     * poll time, as {@link InMemoryBroker} does). {@link #commit} / {@link #reject} are no-ops —
     * the push consumer auto-acks.
     */
    static final class ResponseSideConsumer implements BrokerForwardingConsumerPort {
        private final LinkedBlockingQueue<MessageExt> queue;
        private final long pollWaitMs;
        // subscribed filter (set at subscribe time — D4); the push consumer pre-gets messages from
        // the broker (subscribed with "*" in @BeforeAll), so this filter is applied client-side at poll.
        private volatile DeliveryFilter filter;
        private volatile String consumerServiceId;

        ResponseSideConsumer(LinkedBlockingQueue<MessageExt> queue, long pollWaitMs) {
            this.queue = queue;
            this.pollWaitMs = pollWaitMs;
        }

        @Override
        public void subscribe(String consumerServiceId, ForwardingRouteHandle route, DeliveryFilter filter) {
            this.consumerServiceId = consumerServiceId;
            this.filter = Objects.requireNonNull(filter, "filter is required");
        }

        @Override
        public Optional<BrokerInboundMessage> poll(long nowMillisEpoch) {
            DeliveryFilter f = filter;
            if (f == null) {
                throw new IllegalStateException("polled before subscribe");
            }
            long deadlineMs = nowMillisEpoch + pollWaitMs;
            while (true) {
                long remaining = deadlineMs - System.currentTimeMillis();
                if (remaining <= 0) {
                    return Optional.empty();
                }
                MessageExt ext;
                try {
                    ext = queue.poll(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
                if (ext == null) {
                    return Optional.empty();
                }
                // apply the subscribed filter client-side (D7 degrade; the push consumer subscribes
                // with "*", so broker-side filtering is not in effect — supportsBrokerSidePropertyFilter=false).
                if (!matchesFilter(ext, f)) {
                    continue;
                }
                return Optional.of(new BrokerInboundMessage(
                        ext.getProperty("tenantId"),
                        ext.getProperty("messageId"),
                        ext.getProperty("sourceServiceId"),
                        ext.getProperty("targetServiceId"),
                        consumerServiceId,
                        ext.getProperty("payloadRef"),
                        ext.getProperty("correlationId"),
                        softEventType(ext.getProperty("eventType"))));
            }
        }

        @Override
        public void commit(BrokerInboundMessage message) {
            // push consumer auto-acks at receipt; no offset to advance.
        }

        @Override
        public void reject(BrokerInboundMessage message, ForwardingFailureCode code) {
            // push consumer auto-acks; reject is observability-only (not modelled here).
        }

        @Override
        public void close() {
            // the push consumer is shut down in @AfterAll; per-instance no-op.
        }

        @Override
        public boolean supportsBrokerSidePropertyFilter() {
            return false; // push consumer subscribes with "*"; filtering is client-side at poll (D7 degrade)
        }

        private static boolean matchesFilter(MessageExt ext, DeliveryFilter f) {
            for (Map.Entry<String, String> c : f.requiredProperties().entrySet()) {
                String actual = ext.getProperty(c.getKey());
                if (actual == null || !actual.equals(c.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private static AgentBusEventType softEventType(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            try {
                return AgentBusEventType.valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    // ===== temp agent-runtime (mimics TestAgentRuntime against the real broker) =====

    /**
     * Stands in for the EXTERNAL {@code agent-runtime-java} (S4 consumer lands there). Consumes
     * request events from {@code ascend_bus_invocation_req}, "processes" per the L2 §4.3 response
     * state machine, and produces response events back onto {@code ascend_bus_invocation_resp_out}
     * via a {@link DefaultMQProducer}, mirroring {@link RocketMqBrokerForwardingRelay#buildMessage}
     * so the responseConsumer reads the same routing user-properties.
     */
    static final class TempRuntime {

        /** Configurable response behaviour for a consumed REQUESTED event (mirrors TestAgentRuntime). */
        enum ResponseMode { BLOCKING, SILENT, STREAMING }

        private final DefaultMQPushConsumer consumer;
        private final DefaultMQProducer producer;
        private final AtomicLong taskSeq = new AtomicLong();
        private final AtomicLong respSeq = new AtomicLong();
        private final Map<String, TaskEntry> taskByKey = new ConcurrentHashMap<>();
        private volatile ResponseMode responseMode = ResponseMode.BLOCKING;

        TempRuntime(String nameserver) {
            consumer = new DefaultMQPushConsumer("it-temp-runtime");
            consumer.setNamesrvAddr(nameserver);
            producer = new DefaultMQProducer("it-temp-runtime-producer");
            producer.setNamesrvAddr(nameserver);
        }

        void start() throws Exception {
            producer.start();
            consumer.subscribe(TOPIC_INVOCATION_REQ, "*");
            consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
                for (MessageExt req : msgs) {
                    processRequest(req);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
        }

        void shutdown() {
            if (consumer != null) {
                consumer.shutdown();
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

        private synchronized void processRequest(MessageExt req) {
            String payloadRef = req.getProperty("payloadRef");
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
            String reqTenant = req.getProperty("tenantId");
            String reqSource = req.getProperty("sourceServiceId"); // = GATEWAY (response target)
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
         * UC-6 (per-eventType classify) can inject a chosen eventType directly without consuming
         * a request; {@code source=RUNTIME}, {@code target=GATEWAY} (the response swap).
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

        /** UC-6 direct-injection overload: response-swap defaults (source=RUNTIME, target=GATEWAY, tenant=TENANT). */
        void produceResponse(AgentBusEventType eventType, String respPayloadRef, String correlationId) {
            produceResponse(eventType, respPayloadRef, correlationId, TENANT, RUNTIME, GATEWAY);
        }
    }
}
