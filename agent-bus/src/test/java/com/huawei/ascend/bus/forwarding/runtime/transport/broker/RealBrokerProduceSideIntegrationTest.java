package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import com.huawei.ascend.bus.forwarding.runtime.transport.MapEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq.RocketMqBrokerForwardingRelay;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingConsumerPort;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerInboundMessage;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerProduceOutcome;
import com.huawei.ascend.bus.forwarding.spi.broker.DeliveryFilter;
import com.huawei.ascend.bus.forwarding.test.InMemoryForwardingOutbox;
import com.huawei.ascend.bus.gateway.runtime.GatewayRuntimeService;
import com.huawei.ascend.bus.spi.ingress.IngressEnvelope;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S7 prototype — real-RocketMQ PRODUCE-side integration test for FEAT-013/014
 * (REQ-2026-001 集成联调, env-guarded).
 *
 * <p>Validates the S2/S3 produce path against a real broker:
 * {@link GatewayRuntimeService#dispatchRequest} → {@link RocketMqBrokerForwardingRelay}
 * (real {@link DefaultMQProducer} via {@link RocketMqBrokerForwardingRelay#defaultSender})
 * → broker topic, drained by a temp {@link DefaultMQPushConsumer} that mimics the
 * consume side (S4 receiver consumer is deferred, so this is the联调 bridge). Asserts
 * the message lands on the right topic with all routing user-properties
 * (tenantId/messageId/sourceServiceId/targetServiceId/correlationId/eventType/payloadRef)
 * and that {@link BrokerControlDescriptor#decode} round-trips the request control fields.
 *
 * <p><b>Env-guarded.</b> Skipped unless the {@code ROCKETMQ_NAMESERVER} environment
 * variable is set (no broker → skip → suite stays green). Run against a live broker with:
 * <pre>{@code
 *   ROCKETMQ_NAMESERVER=<host>:9876 ../mvnw test -Dtest=RealBrokerProduceSideIntegrationTest
 * }</pre>
 * On Windows Git Bash: {@code ROCKETMQ_NAMESERVER=7.213.203.4:9876 ../mvnw test -Dtest=...}.
 *
 * <p>Uses JUnit-native {@link EnabledIfEnvironmentVariable} (NOT Spring's
 * {@code @EnabledIfEnvironmentProperty}) because agent-bus has no {@code @SpringBootTest}
 * anywhere — every test is plain JUnit 5, and the Spring annotation is not evaluated
 * without a {@code SpringExtension}. Same env-guard intent, framework-independent.
 *
 * <p>UC coverage (handoff UC table): UC-2 (client invocation produce — the full
 * gateway path) + UC-3 (A2A call request produce). UC-3 is driven DIRECTLY via
 * {@code relay.produce} (not {@code gateway.dispatchRequest}) because the S2
 * client-ingress gateway only maps {@code IngressRequestType} →
 * {@code CLIENT_INVOCATION_*}; {@code A2A_CALL_REQUESTED} is not reachable through the
 * client-ingress path (A2A ingress is deferred to S4). This gap between the handoff
 * UC table and the implemented S2 gateway is a联调-discovered finding.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md} §4.2 / §5.2;
 * {@code architecture/L2-Low-Level-Design/agent-bus/feat-014-a2a-call-event-forwarding.md} §5.2.
 */
// scope: forwarding transport.broker test — real-RocketMQ produce-side IT (env-guarded, S7 prototype)
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_NAMESERVER", matches = ".+")
@Isolated
class RealBrokerProduceSideIntegrationTest {

    private static final String TENANT = "tenant-a";
    private static final String GATEWAY = "it-gateway";
    private static final String RUNTIME = "it-agent-runtime";
    private static final String TRACE = "0123456789abcdef0123456789abcdef";
    private static final String ROUTE_INVOCATION = "route-invocation";
    private static final String ROUTE_A2A = "route-a2a";
    // RocketMQ topic-name validator (^[%|a-zA-Z0-9_-]+$) forbids '.' — 联调 surfaced
    // that the L2 §5.2 + docker-compose.yml originally used dotted names
    // (ascend.bus.invocation.req), which mqadmin rejects. Reconciled to underscore
    // (ascend_bus_invocation_req) across the L2 docs + docker-compose + this IT.
    private static final String TOPIC_INVOCATION_REQ = "ascend_bus_invocation_req";
    private static final String TOPIC_A2A_REQ = "ascend_bus_a2a_req";
    private static final long CAPTURE_TIMEOUT_MS = 10_000L;

    private static DefaultMQProducer producer;
    private static DefaultMQPushConsumer drainConsumer;
    private static final ConcurrentLinkedQueue<MessageExt> captured = new ConcurrentLinkedQueue<>();

    @BeforeAll
    static void startBrokerClients() throws Exception {
        String nameserver = System.getenv("ROCKETMQ_NAMESERVER");
        producer = new DefaultMQProducer("it-gateway-producer");
        producer.setNamesrvAddr(nameserver);
        producer.start();

        // Temp drain consumer (S4 receiver consumer is deferred). Subscribes to the two
        // request topics and captures produced messages so the test can assert them.
        drainConsumer = new DefaultMQPushConsumer("it-drain-consumer");
        drainConsumer.setNamesrvAddr(nameserver);
        drainConsumer.subscribe(TOPIC_INVOCATION_REQ, "*");
        drainConsumer.subscribe(TOPIC_A2A_REQ, "*");
        drainConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            captured.addAll(msgs);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        drainConsumer.start();
        // Default CONSUME_FROM_LAST_OFFSET consumes only messages produced AFTER the
        // consumer is assigned queues; let rebalance settle before producing.
        Thread.sleep(3_000L);
    }

    @AfterAll
    static void shutdownBrokerClients() {
        if (drainConsumer != null) {
            drainConsumer.shutdown();
        }
        if (producer != null) {
            producer.shutdown();
        }
    }

    private InMemoryForwardingOutbox outbox;
    private RocketMqBrokerForwardingRelay relay;
    private GatewayRuntimeService gateway;

    @BeforeEach
    void wireGateway() {
        captured.clear();
        outbox = new InMemoryForwardingOutbox();
        MapEndpointResolver resolver = new MapEndpointResolver(Map.of(
                ROUTE_INVOCATION, TOPIC_INVOCATION_REQ,
                ROUTE_A2A, TOPIC_A2A_REQ));
        relay = new RocketMqBrokerForwardingRelay(
                resolver, RocketMqBrokerForwardingRelay.defaultSender(producer));
        // Produce-side tests do not poll responses; a no-op responseConsumer satisfies the ctor.
        BrokerForwardingConsumerPort noopConsumer = new BrokerForwardingConsumerPort() {
            @Override
            public void subscribe(String consumerServiceId, ForwardingRouteHandle route, DeliveryFilter filter) {
                // produce-side IT never polls responses; no-op.
            }
            @Override
            public Optional<BrokerInboundMessage> poll(long nowMillisEpoch) {
                return Optional.empty();
            }
            @Override
            public void commit(BrokerInboundMessage message) {
                // no-op
            }
            @Override
            public void reject(BrokerInboundMessage message, ForwardingFailureCode code) {
                // no-op
            }
            @Override
            public void close() {
                // no-op
            }
            @Override
            public boolean supportsBrokerSidePropertyFilter() {
                return false;
            }
        };
        gateway = new GatewayRuntimeService(outbox, outbox, relay, noopConsumer,
                GATEWAY, 5_000L, 30_000L, System::currentTimeMillis);
    }

    /**
     * UC-2 — a client RUN_CREATE request dispatched through the gateway lands on the
     * FEAT-013 request topic with complete routing headers and a round-trip control
     * descriptor in the payloadRef.
     */
    @Test
    void uc2_clientInvocationRequest_landsOnBrokerWithHeadersAndDescriptor() throws Exception {
        UUID requestId = UUID.randomUUID();
        IngressEnvelope env = new IngressEnvelope(
                requestId, TENANT, UUID.randomUUID(),
                IngressEnvelope.IngressRequestType.RUN_CREATE,
                "payload-body", TRACE, /* deadlineMillisEpoch */ null,
                Map.of("routeHandle", ROUTE_INVOCATION, "targetServiceId", RUNTIME));

        ForwardingEnvelope envelope = gateway.dispatchRequest(env);

        MessageExt received = awaitCapture(envelope.correlationId());
        assertThat(received.getTopic()).isEqualTo(TOPIC_INVOCATION_REQ);
        assertThat(received.getProperty("tenantId")).isEqualTo(TENANT);
        assertThat(received.getProperty("messageId")).isEqualTo(envelope.messageId().value());
        assertThat(received.getProperty("sourceServiceId")).isEqualTo(GATEWAY);
        assertThat(received.getProperty("targetServiceId")).isEqualTo(RUNTIME);
        assertThat(received.getProperty("correlationId")).isEqualTo(requestId.toString());
        assertThat(received.getProperty("eventType")).isEqualTo("CLIENT_INVOCATION_REQUESTED");
        // body is the routing descriptor only (§6.2 ②)
        assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).isEqualTo("target=" + RUNTIME);

        String payloadRef = received.getProperty("payloadRef");
        assertThat(payloadRef).isNotBlank();
        BrokerControlDescriptor.Descriptor decoded = BrokerControlDescriptor.decode(payloadRef);
        assertThat(decoded.eventType()).isEqualTo(AgentBusEventType.CLIENT_INVOCATION_REQUESTED);
        assertThat(decoded.traceId()).isEqualTo(TRACE);
        assertThat(decoded.correlationId()).isEqualTo(requestId.toString());
        assertThat(decoded.idempotencyKey()).isEqualTo(env.idempotencyKey().toString());
        assertThat(decoded.routeHandle()).isEqualTo(ROUTE_INVOCATION);
        assertThat(decoded.capability()).isEqualTo("a2a"); // default when omitted
        assertThat(decoded.deadlineMillisEpoch()).isEqualTo(Long.MAX_VALUE); // null deadline → Long.MAX_VALUE
    }

    /**
     * UC-3 — an A2A call request produced via the relay lands on the FEAT-014 A2A
     * request topic. Driven directly through {@code relay.produce} (not
     * {@code gateway.dispatchRequest}) because the S2 client-ingress gateway cannot
     * emit {@code A2A_CALL_REQUESTED} (A2A ingress is deferred to S4).
     */
    @Test
    void uc3_a2aCallRequest_producedViaRelay_landsOnA2aTopic() throws Exception {
        String correlationId = "a2a-" + UUID.randomUUID();
        String idempotencyKey = "idem-" + UUID.randomUUID();
        String descriptor = BrokerControlDescriptor.encode(
                AgentBusEventType.A2A_CALL_REQUESTED, TRACE, correlationId,
                idempotencyKey, ROUTE_A2A, "a2a", Long.MAX_VALUE);
        ForwardingEnvelope envelope = new ForwardingEnvelope(
                new ForwardingMessageId("gw-" + UUID.randomUUID()),
                AgentBusEventType.A2A_CALL_REQUESTED, TENANT, TRACE, correlationId,
                idempotencyKey, new ForwardingRouteHandle(ROUTE_A2A, TENANT), "a2a",
                GATEWAY, RUNTIME, Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.DATA_BEARING, descriptor);

        ForwardingReceipt receipt = outbox.enqueue(envelope, GATEWAY, RUNTIME, System.currentTimeMillis());
        assertThat(receipt).isNotNull();
        List<ForwardingOutboxRecord> claimed = outbox.claimDue(
                TENANT, System.currentTimeMillis(), 1, GATEWAY,
                System.currentTimeMillis() + 60_000L);
        assertThat(claimed).hasSize(1);

        BrokerProduceOutcome outcome = relay.produce(claimed.get(0), System.currentTimeMillis());
        assertThat(outcome.outcome()).isEqualTo(BrokerProduceOutcome.Outcome.ACCEPTED);

        MessageExt received = awaitCapture(correlationId);
        assertThat(received.getTopic()).isEqualTo(TOPIC_A2A_REQ);
        assertThat(received.getProperty("eventType")).isEqualTo("A2A_CALL_REQUESTED");
        assertThat(received.getProperty("correlationId")).isEqualTo(correlationId);
        assertThat(received.getProperty("tenantId")).isEqualTo(TENANT);
        assertThat(received.getProperty("sourceServiceId")).isEqualTo(GATEWAY);
        assertThat(received.getProperty("targetServiceId")).isEqualTo(RUNTIME);
        BrokerControlDescriptor.Descriptor decoded =
                BrokerControlDescriptor.decode(received.getProperty("payloadRef"));
        assertThat(decoded.eventType()).isEqualTo(AgentBusEventType.A2A_CALL_REQUESTED);
        assertThat(decoded.routeHandle()).isEqualTo(ROUTE_A2A);
    }

    /** Drain the captured queue for the message whose correlationId user-property matches. */
    private MessageExt awaitCapture(String correlationId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + CAPTURE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            for (MessageExt m : captured) {
                if (correlationId.equals(m.getProperty("correlationId"))) {
                    return m;
                }
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("no captured message with correlationId=" + correlationId
                + " within " + CAPTURE_TIMEOUT_MS + "ms; captured total=" + captured.size());
    }
}
