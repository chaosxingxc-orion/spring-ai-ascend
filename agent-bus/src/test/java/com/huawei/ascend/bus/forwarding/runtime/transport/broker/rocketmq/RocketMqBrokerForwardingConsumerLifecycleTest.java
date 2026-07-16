package com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq;

import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.MapEndpointResolver;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerInboundMessage;
import com.huawei.ascend.bus.forwarding.spi.broker.DeliveryFilter;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link RocketMqBrokerForwardingConsumer} consumer lifecycle (decision packet
 * agent-bus-broker-filtering-spi-completion §7 slice 2 / §3 D4/D5/D8). Pins the broker-independent
 * adapter behavior — subscribe wiring (resolver→topic, DeliveryFilter→SQL92), poll mapping
 * ({@code MessageExt}→{@link BrokerInboundMessage} with the polling consumerServiceId), commit /
 * reject (model B in-flight tracking), close, capability — via a fake
 * {@link RocketMqBrokerForwardingConsumer.MessagePollerFactory} seam (no live broker). The real
 * {@code DefaultLitePullConsumer} round-trip is the env-guarded real-broker IT (slice 3 / §6 D4).
 *
 * <p>Mirrors {@link RocketMqBrokerForwardingRelayTest} (fake {@code MessageSender}, no producer) —
 * the consumer-side twin of that seam-driven unit test.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/
 * agent-bus-broker-filtering-spi-completion-decision.md} §3 (D3/D4/D5/D8/D14) / §7 slice 2.
 */
class RocketMqBrokerForwardingConsumerLifecycleTest {

    private static final String TENANT = "tenant-a";
    private static final String ROUTE = "route-for-tenant-a";
    private static final String SOURCE = "source-svc";
    private static final String TARGET = "target-svc";

    // ===== subscribe: resolver→topic + filter→SQL92, lazy poller creation =====

    @Test
    void subscribe_wires_resolver_topic_and_sql92_filter_and_lazily_creates_poller() {
        RecordingFactory factory = new RecordingFactory();
        RocketMqBrokerForwardingConsumer consumer = new RocketMqBrokerForwardingConsumer(
                resolver(ROUTE, "topic-from-resolver"), factory, 1_000L);

        consumer.subscribe("consumer-a", routeHandle(ROUTE),
                DeliveryFilter.forRuntime(TENANT, TARGET));

        // §3 lazy registration: the poller (DefaultLitePullConsumer, group=consumerServiceId) is
        // created at subscribe, not construction.
        assertThat(factory.createdGroups).containsExactly("consumer-a");
        RecordingPoller poller = factory.lastPoller();
        // HD4: the route is resolved to a topic via the injected resolver (never routeHandle.value()).
        assertThat(poller.subscribedTopics).containsExactly("topic-from-resolver");
        // D3: the filter is translated to SQL92 HERE; the prod poller wraps it in MessageSelector.bySql.
        assertThat(poller.subscribedSqls).containsExactly(
                RocketMqBrokerForwardingConsumer.sql92Expression(
                        DeliveryFilter.forRuntime(TENANT, TARGET)));
    }

    @Test
    void subscribe_accumulates_a_second_route_on_the_same_poller() {
        RecordingFactory factory = new RecordingFactory();
        String routeOther = "route-other";
        RocketMqBrokerForwardingConsumer consumer = new RocketMqBrokerForwardingConsumer(
                new MapEndpointResolver(Map.of(ROUTE, "topic-a", routeOther, "topic-b")),
                factory, 1_000L);

        consumer.subscribe("consumer-a", routeHandle(ROUTE), DeliveryFilter.forRuntime(TENANT, TARGET));
        consumer.subscribe("consumer-a", routeHandle(routeOther), DeliveryFilter.forRuntime(TENANT, "other-svc"));

        // lazy: the poller is created ONCE (one consumer-group); the second subscribe accumulates a route
        // (RocketMQ multi-subscribe on the one consumer-group).
        assertThat(factory.createdGroups).containsExactly("consumer-a");
        assertThat(factory.lastPoller().subscribedTopics).containsExactly("topic-a", "topic-b");
        assertThat(factory.lastPoller().subscribedSqls).hasSize(2);
    }

    // ===== poll: MessageExt → BrokerInboundMessage, materialised consumerServiceId, in-flight tracked =====

    @Test
    void poll_maps_polled_message_ext_to_inbound_with_subscribed_consumer_service_id() {
        RecordingFactory factory = new RecordingFactory();
        RocketMqBrokerForwardingConsumer consumer = new RocketMqBrokerForwardingConsumer(
                resolver(ROUTE, "topic-x"), factory, 1_000L);
        consumer.subscribe("consumer-a", routeHandle(ROUTE), DeliveryFilter.forRuntime(TENANT, TARGET));
        factory.lastPoller().enqueue(
                messageExt("msg-1", "ref-1", "corr-1", "CLIENT_INVOCATION_REQUESTED"));

        BrokerInboundMessage m = consumer.poll(2_000L).orElseThrow();

        // routing headers mirror the polled ext; consumerServiceId is materialised at poll time (T4).
        assertThat(m.tenantId()).isEqualTo(TENANT);
        assertThat(m.messageId()).isEqualTo("msg-1");
        assertThat(m.sourceServiceId()).isEqualTo(SOURCE);
        assertThat(m.targetServiceId()).isEqualTo(TARGET);
        assertThat(m.consumerServiceId()).isEqualTo("consumer-a");
        assertThat(m.payloadRef()).isEqualTo("ref-1");
        assertThat(m.correlationId()).isEqualTo("corr-1");
        assertThat(m.eventType()).isEqualTo(AgentBusEventType.CLIENT_INVOCATION_REQUESTED);
    }

    @Test
    void poll_returns_empty_when_poller_has_no_message() {
        RocketMqBrokerForwardingConsumer consumer = consumerWith("consumer-a");

        assertThat(consumer.poll(2_000L)).isEmpty();
    }

    @Test
    void poll_before_subscribe_throws() {
        RocketMqBrokerForwardingConsumer consumer = new RocketMqBrokerForwardingConsumer(
                resolver(ROUTE, "topic-x"), new RecordingFactory(), 1_000L);

        assertThatThrownBy(() -> consumer.poll(2_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subscribe");
    }

    // ===== commit / reject: model B ack-after-consume, in-flight tracking =====

    @Test
    void commit_delegates_to_poller_with_the_polled_ext_and_is_idempotent() {
        RecordingFactory factory = new RecordingFactory();
        RocketMqBrokerForwardingConsumer consumer = new RocketMqBrokerForwardingConsumer(
                resolver(ROUTE, "topic-x"), factory, 1_000L);
        consumer.subscribe("consumer-a", routeHandle(ROUTE), DeliveryFilter.forRuntime(TENANT, TARGET));
        RecordingPoller poller = factory.lastPoller();
        poller.enqueue(messageExt("msg-1", null, null, null));
        BrokerInboundMessage m = consumer.poll(2_000L).orElseThrow();

        consumer.commit(m);

        // the polled ext is acked (model B ack-after-consume); a re-commit of the same message is a
        // no-op — it was already dropped from in-flight (idempotent).
        assertThat(poller.committed).hasSize(1);
        assertThat(poller.committed.get(0).getProperty("messageId")).isEqualTo("msg-1");
        consumer.commit(m);
        assertThat(poller.committed).hasSize(1);
    }

    @Test
    void reject_delegates_to_poller_with_code_without_committing() {
        RecordingFactory factory = new RecordingFactory();
        RocketMqBrokerForwardingConsumer consumer = new RocketMqBrokerForwardingConsumer(
                resolver(ROUTE, "topic-x"), factory, 1_000L);
        consumer.subscribe("consumer-a", routeHandle(ROUTE), DeliveryFilter.forRuntime(TENANT, TARGET));
        RecordingPoller poller = factory.lastPoller();
        poller.enqueue(messageExt("msg-1", null, null, null));
        BrokerInboundMessage m = consumer.poll(2_000L).orElseThrow();

        consumer.reject(m, ForwardingFailureCode.TENANT_MISMATCH);

        // reject does NOT ack → the broker redelivers (at-least-once); the code is recorded for observability.
        assertThat(poller.committed).isEmpty();
        assertThat(poller.rejected).hasSize(1);
        assertThat(poller.rejected.get(0).getKey().getProperty("messageId")).isEqualTo("msg-1");
        assertThat(poller.rejected.get(0).getValue()).isEqualTo(ForwardingFailureCode.TENANT_MISMATCH);
    }

    // ===== close / capability (D8 / §3 close) =====

    @Test
    void close_delegates_to_poller() {
        RecordingFactory factory = new RecordingFactory();
        RocketMqBrokerForwardingConsumer consumer = new RocketMqBrokerForwardingConsumer(
                resolver(ROUTE, "topic-x"), factory, 1_000L);
        consumer.subscribe("consumer-a", routeHandle(ROUTE), DeliveryFilter.forRuntime(TENANT, TARGET));
        RecordingPoller poller = factory.lastPoller();

        consumer.close();

        assertThat(poller.closed).isTrue();
    }

    @Test
    void close_before_subscribe_is_null_safe() {
        RocketMqBrokerForwardingConsumer consumer = new RocketMqBrokerForwardingConsumer(
                resolver(ROUTE, "topic-x"), new RecordingFactory(), 1_000L);
        // no subscribe → the poller is null; close must not NPE (idempotent, null-safe per §3 close).
        consumer.close();
    }

    @Test
    void supports_broker_side_property_filter_is_true() {
        RocketMqBrokerForwardingConsumer consumer = new RocketMqBrokerForwardingConsumer(
                resolver(ROUTE, "topic-x"), new RecordingFactory(), 1_000L);
        // RocketMQ SQL92 filters broker-side (D8) — the receiver only ever receives filter-matching messages.
        assertThat(consumer.supportsBrokerSidePropertyFilter()).isTrue();
    }

    // ===== fixtures =====

    private static ForwardingEndpointResolver resolver(String routeValue, String topic) {
        return new MapEndpointResolver(Map.of(routeValue, topic));
    }

    private static ForwardingRouteHandle routeHandle(String routeValue) {
        return new ForwardingRouteHandle(routeValue, TENANT);
    }

    /** Wire a consumer subscribed as {@code group} on ROUTE→"topic-x" with a recording factory. */
    private static RocketMqBrokerForwardingConsumer consumerWith(String group) {
        RecordingFactory factory = new RecordingFactory();
        RocketMqBrokerForwardingConsumer consumer = new RocketMqBrokerForwardingConsumer(
                resolver(ROUTE, "topic-x"), factory, 1_000L);
        consumer.subscribe(group, routeHandle(ROUTE), DeliveryFilter.forRuntime(TENANT, TARGET));
        return consumer;
    }

    private static MessageExt messageExt(String messageId, String payloadRef,
                                         String correlationId, String eventType) {
        MessageExt ext = new MessageExt();
        ext.putUserProperty("tenantId", TENANT);
        ext.putUserProperty("messageId", messageId);
        ext.putUserProperty("sourceServiceId", SOURCE);
        ext.putUserProperty("targetServiceId", TARGET);
        if (payloadRef != null) {
            ext.putUserProperty("payloadRef", payloadRef);
        }
        if (correlationId != null) {
            ext.putUserProperty("correlationId", correlationId);
        }
        if (eventType != null) {
            ext.putUserProperty("eventType", eventType);
        }
        return ext;
    }

    // ===== fake seam: factory + poller (no live broker) =====

    static final class RecordingFactory implements RocketMqBrokerForwardingConsumer.MessagePollerFactory {
        final List<String> createdGroups = new ArrayList<>();
        private RecordingPoller last;

        @Override
        public RecordingPoller pollerFor(String consumerGroup) {
            createdGroups.add(consumerGroup);
            last = new RecordingPoller();
            return last;
        }

        RecordingPoller lastPoller() {
            return last;
        }
    }

    static final class RecordingPoller implements RocketMqBrokerForwardingConsumer.MessagePoller {
        final List<String> subscribedTopics = new ArrayList<>();
        final List<String> subscribedSqls = new ArrayList<>();
        final Queue<MessageExt> pollReturns = new LinkedList<>();
        final List<MessageExt> committed = new ArrayList<>();
        final List<Map.Entry<MessageExt, ForwardingFailureCode>> rejected = new ArrayList<>();
        boolean closed;

        void enqueue(MessageExt ext) {
            pollReturns.add(ext);
        }

        @Override
        public void subscribe(String topic, String sql92Expression) {
            subscribedTopics.add(topic);
            subscribedSqls.add(sql92Expression);
        }

        @Override
        public Optional<MessageExt> poll(long timeoutMillis) {
            return Optional.ofNullable(pollReturns.poll());
        }

        @Override
        public void commit(MessageExt message) {
            committed.add(message);
        }

        @Override
        public void reject(MessageExt message, ForwardingFailureCode code) {
            rejected.add(Map.entry(message, code));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
