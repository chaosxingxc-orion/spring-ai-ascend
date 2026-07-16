package com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq;

import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingRelayPort;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerProduceOutcome;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * RocketMQ concrete adapter for {@link BrokerForwardingRelayPort} (FEAT-013/014, S3).
 *
 * <p>Maps a claimed {@link ForwardingOutboxRecord} onto a RocketMQ {@link Message} and
 * produces it via a {@link DefaultMQProducer}-backed sender. The opaque
 * {@code routeHandle} is resolved to a topic by the injected
 * {@link ForwardingEndpointResolver} (HD4 preserved — the adapter never reads
 * {@code routeHandle.value()}). The message body is a routing descriptor only
 * (decision §6.2 ②); routing metadata (tenantId / messageId / source / target /
 * correlationId / payloadRef) rides as RocketMQ user properties — never the payload
 * body, a token stream, or Task state.
 *
 * <p><b>Testability seam.</b> The {@link MessageSender} functional interface isolates the
 * {@code producer.send} call so unit tests inject a fake (no live broker). The real
 * {@link DefaultMQProducer}-backed sender is {@link #defaultSender(DefaultMQProducer)};
 * its lifecycle (start / shutdown) is wired by the Stage 27+ runtime config (S4), not
 * here. The real-broker round-trip is the S7 {@code @EnabledIfEnvironmentProperty}
 * integration test (env-guarded, not a unit test).
 *
 * <p>Broker native retry is OFF — the agent-bus retry policy (Stage 14) leads, so an
 * {@link BrokerProduceOutcome.Outcome#UNAVAILABLE UNAVAILABLE} outcome surfaces a
 * retryable failure for the policy to schedule, never a double-retry.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md} §4.1 / §5.2 / §8.1;
 * {@code feat-014-a2a-call-event-forwarding.md} §5.2.
 */
// scope: forwarding transport.broker — concrete RocketMQ relay adapter (SPI-licensed, ArchUnit-confined)
public final class RocketMqBrokerForwardingRelay implements BrokerForwardingRelayPort {

    /**
     * Seam for sending a built {@link Message} — unit tests inject a fake (no live broker);
     * prod injects a {@link DefaultMQProducer}-backed sender via {@link #defaultSender}.
     */
    @FunctionalInterface
    public interface MessageSender {
        /** Send the message; throw on any failure (the relay maps it to UNAVAILABLE). */
        void send(Message message) throws Exception;
    }

    private final ForwardingEndpointResolver resolver;
    private final MessageSender sender;

    public RocketMqBrokerForwardingRelay(ForwardingEndpointResolver resolver, MessageSender sender) {
        this.resolver = Objects.requireNonNull(resolver, "resolver is required");
        this.sender = Objects.requireNonNull(sender, "sender is required");
    }

    @Override
    public BrokerProduceOutcome produce(ForwardingOutboxRecord record, long nowMillisEpoch) {
        Objects.requireNonNull(record, "record is required");
        // HD4: resolve routeHandle via the injected resolver, never reading routeHandle.value().
        Optional<String> topic = resolver.resolve(record.routeHandle());
        if (topic.isEmpty()) {
            return BrokerProduceOutcome.routeNotFound(ForwardingFailureCode.ROUTE_NOT_FOUND);
        }
        Message message = buildMessage(record, topic.get());
        try {
            sender.send(message);
            return BrokerProduceOutcome.accepted();
        } catch (InterruptedException e) {
            // restore the interrupt status — the worker / dispatch loop must observe the interrupt.
            Thread.currentThread().interrupt();
            return BrokerProduceOutcome.unavailable(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
        } catch (Exception e) {
            // MQClientException / MQBrokerException / RemotingException — broker transiently
            // unreachable; surface retryable UNAVAILABLE for the agent-bus retry policy.
            return BrokerProduceOutcome.unavailable(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
        }
    }

    /**
     * Pure mapping: a claimed outbox record → RocketMQ {@link Message}. Body is a routing
     * descriptor only (§6.2 ②); routing metadata rides as user properties (incl.
     * {@code correlationId} per FEAT-013 §4.2 so the gateway can match responses).
     * {@code keys} = messageId (broker-side query / dedup hook). Extracted static so unit
     * tests pin the mapping without a producer.
     */
    static Message buildMessage(ForwardingOutboxRecord record, String topic) {
        Message message = new Message(
                topic,
                /* tags */ null,
                /* keys */ record.messageId().value(),
                buildRoutingDescriptor(record).getBytes(StandardCharsets.UTF_8));
        message.putUserProperty("tenantId", record.tenantId());
        message.putUserProperty("messageId", record.messageId().value());
        message.putUserProperty("sourceServiceId", record.sourceServiceId());
        message.putUserProperty("targetServiceId", record.targetServiceId());
        if (record.correlationId() != null) {
            message.putUserProperty("correlationId", record.correlationId());
        }
        if (record.eventType() != null) {
            message.putUserProperty("eventType", record.eventType().name());
        }
        if (record.payloadRef() != null) {
            message.putUserProperty("payloadRef", record.payloadRef());
        }
        return message;
    }

    /** §6.2 ②: the broker body is a short routing descriptor only. */
    static String buildRoutingDescriptor(ForwardingOutboxRecord record) {
        return "target=" + record.targetServiceId();
    }

    /**
     * Production sender backed by a started {@link DefaultMQProducer}. A non-{@code SEND_OK}
     * send status (replication timeout / slave unavailable) surfaces as an exception so the
     * relay maps it to {@code UNAVAILABLE} (retryable) — the agent-bus retry policy re-drives
     * and the outbox dedups on {@code (tenantId, messageId)}, preserving at-least-once.
     */
    public static MessageSender defaultSender(DefaultMQProducer producer) {
        Objects.requireNonNull(producer, "producer is required");
        return message -> {
            SendResult result = producer.send(message);
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException(
                        "rocketmq produce non-OK send status: " + result.getSendStatus());
            }
        };
    }
}
