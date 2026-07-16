package com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq;

import com.huawei.ascend.bus.forwarding.runtime.transport.MapEndpointResolver;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerProduceOutcome;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RocketMqBrokerForwardingRelay} (FEAT-013/014, S3).
 *
 * <p>Pins the pure record&rarr;{@link Message} mapping (decision §6.2 ② routing-descriptor
 * body; routing-metadata user properties incl. {@code correlationId} per FEAT-013 §4.2 so
 * the gateway can match responses) and the {@code produce} outcome mapping
 * (routeNotFound / accepted / unavailable) via a fake
 * {@link RocketMqBrokerForwardingRelay.MessageSender} — no live broker (the real
 * {@code DefaultMQProducer} round-trip is the S7 {@code @EnabledIfEnvironmentProperty}
 * integration test, env-guarded).
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md} §4.1 / §5.2 / §8.1.
 */
class RocketMqBrokerForwardingRelayTest {

    private static final String TENANT = "tenant-a";
    private static final String ROUTE = "route-for-tenant-a";
    private static final String SOURCE = "source-svc";
    private static final String TARGET = "target-svc";

    // ===== buildMessage: record → RocketMQ Message (pure mapping) =====

    @Test
    void buildMessage_maps_record_to_routing_descriptor_body_and_metadata_properties() {
        ForwardingOutboxRecord record = record("msg-1", "payload-ref-1", "corr-1");

        Message msg = RocketMqBrokerForwardingRelay.buildMessage(record, "topic-from-resolver");

        assertThat(msg.getTopic()).isEqualTo("topic-from-resolver");
        // §6.2 ②: body is a routing descriptor only — NEVER the payload body.
        assertThat(new String(msg.getBody(), StandardCharsets.UTF_8)).isEqualTo("target=" + TARGET);
        // keys = messageId (broker-side query / dedup hook)
        assertThat(msg.getKeys()).isEqualTo("msg-1");
        // routing metadata rides as user properties
        assertThat(msg.getUserProperty("tenantId")).isEqualTo(TENANT);
        assertThat(msg.getUserProperty("messageId")).isEqualTo("msg-1");
        assertThat(msg.getUserProperty("sourceServiceId")).isEqualTo(SOURCE);
        assertThat(msg.getUserProperty("targetServiceId")).isEqualTo(TARGET);
        // FEAT-013 §4.2: correlationId rides as a user property (gateway matches responses by it)
        assertThat(msg.getUserProperty("correlationId")).isEqualTo("corr-1");
        assertThat(msg.getUserProperty("eventType")).isEqualTo("CLIENT_INVOCATION_REQUESTED");
        assertThat(msg.getUserProperty("payloadRef")).isEqualTo("payload-ref-1");
    }

    @Test
    void buildMessage_omits_correlation_id_and_payload_ref_properties_when_null_control_only() {
        ForwardingOutboxRecord record = record("msg-ctrl", null, null);

        Message msg = RocketMqBrokerForwardingRelay.buildMessage(record, "topic-x");

        assertThat(msg.getUserProperty("correlationId")).isNull();
        assertThat(msg.getUserProperty("payloadRef")).isNull();
        // mandatory routing metadata still present
        assertThat(msg.getUserProperty("tenantId")).isEqualTo(TENANT);
        assertThat(msg.getUserProperty("messageId")).isEqualTo("msg-ctrl");
    }

    @Test
    void buildMessage_body_does_not_leak_payload_ref() {
        // §6.2 ②: the payload reference rides as a header, never in the body.
        ForwardingOutboxRecord record = record("msg-1", "ref-secret-payload", "corr-1");
        Message msg = RocketMqBrokerForwardingRelay.buildMessage(record, "topic-x");
        assertThat(new String(msg.getBody(), StandardCharsets.UTF_8)).doesNotContain("ref-secret-payload");
    }

    // ===== produce: outcome mapping =====

    @Test
    void produce_returns_accepted_when_sender_succeeds() {
        List<Message> sent = new ArrayList<>();
        RocketMqBrokerForwardingRelay relay = new RocketMqBrokerForwardingRelay(
                resolver("topic-from-resolver"), sent::add);

        BrokerProduceOutcome outcome = relay.produce(record("msg-1", "ref-1", "corr-1"), 1_000L);

        assertThat(outcome.outcome()).isEqualTo(BrokerProduceOutcome.Outcome.ACCEPTED);
        assertThat(outcome.failureCode()).isNull();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getTopic()).isEqualTo("topic-from-resolver");
        // the produced message carries the correlationId so the gateway can match the response
        assertThat(sent.get(0).getUserProperty("correlationId")).isEqualTo("corr-1");
    }

    @Test
    void produce_returns_route_not_found_when_resolver_returns_empty() {
        RocketMqBrokerForwardingRelay relay = new RocketMqBrokerForwardingRelay(
                new MapEndpointResolver(Map.of()), msg -> { });

        BrokerProduceOutcome outcome = relay.produce(record("msg-1", null, null), 1_000L);

        assertThat(outcome.outcome()).isEqualTo(BrokerProduceOutcome.Outcome.ROUTE_NOT_FOUND);
        assertThat(outcome.failureCode()).isEqualTo(ForwardingFailureCode.ROUTE_NOT_FOUND);
        assertThat(outcome.failureCode().nonRetryable()).isTrue();
    }

    @Test
    void produce_returns_unavailable_when_sender_throws() {
        RocketMqBrokerForwardingRelay relay = new RocketMqBrokerForwardingRelay(
                resolver("topic-x"), msg -> { throw new RuntimeException("broker down"); });

        BrokerProduceOutcome outcome = relay.produce(record("msg-1", null, "corr-1"), 1_000L);

        assertThat(outcome.outcome()).isEqualTo(BrokerProduceOutcome.Outcome.UNAVAILABLE);
        assertThat(outcome.failureCode()).isEqualTo(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
        assertThat(outcome.failureCode().retryable()).isTrue();
    }

    @Test
    void produce_restores_interrupt_flag_on_InterruptedException() {
        RocketMqBrokerForwardingRelay relay = new RocketMqBrokerForwardingRelay(
                resolver("topic-x"), msg -> { throw new InterruptedException(); });

        try {
            BrokerProduceOutcome outcome = relay.produce(record("msg-1", null, null), 1_000L);
            assertThat(outcome.outcome()).isEqualTo(BrokerProduceOutcome.Outcome.UNAVAILABLE);
            assertThat(outcome.failureCode()).isEqualTo(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
            // the interrupt flag must be restored so the worker / dispatch loop observes it.
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear for subsequent tests
        }
    }

    @Test
    void produce_rejects_null_record() {
        RocketMqBrokerForwardingRelay relay = new RocketMqBrokerForwardingRelay(
                resolver("topic-x"), msg -> { });
        assertThatThrownBy(() -> relay.produce(null, 1_000L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("record");
    }

    // ===== fixtures =====

    private static MapEndpointResolver resolver(String topic) {
        return new MapEndpointResolver(Map.of(ROUTE, topic));
    }

    private static ForwardingOutboxRecord record(String messageId, String payloadRef, String correlationId) {
        return new ForwardingOutboxRecord(
                TENANT,
                new ForwardingMessageId(messageId),
                SOURCE,
                TARGET,
                new ForwardingRouteHandle(ROUTE, TENANT),
                payloadRef,
                ForwardingStatus.Outbox.PENDING,
                0, 0L, 0L, 0L, null, null,
                correlationId,
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED);
    }
}
