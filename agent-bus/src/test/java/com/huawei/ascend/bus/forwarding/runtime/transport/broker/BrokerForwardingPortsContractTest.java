package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract test for the broker SPI scaffold (Stage 26) — verifies the T4 hybrid
 * governance invariants hold on the {@link InMemoryBroker} double, the same way
 * the outbox contract test pins the JDBC adapter contract on
 * {@code InMemoryForwardingOutbox}.
 *
 * <p>Covers each Stage 25 §10 guardrail: payload reference rides in the header
 * never the body (②), routeHandle stays opaque via the resolver (HD4), L2
 * header-tenant-check rejects cross-tenant messages (⑤), consumer-group isolation
 * (L3), and at-least-once redelivery (poll without commit / reject returns the
 * same message again).
 */
class BrokerForwardingPortsContractTest {

    // ===== produce: payloadRef in header, never in body (§6.2②) =====

    @Test
    void produce_data_bearing_carries_payload_ref_in_header_not_body() {
        InMemoryBroker broker = broker(route -> Optional.of("topic-" + route.tenantScope()));
        ForwardingOutboxRecord record = record("tenant-a", "msg-1", "ref-secret-payload");

        BrokerProduceOutcome outcome = broker.produce(record, 1_000L);

        assertThat(outcome.outcome()).isEqualTo(BrokerProduceOutcome.Outcome.ACCEPTED);
        BrokerOutboundMessage stored = broker.outboundMessage("tenant-a", "msg-1");
        assertThat(stored).isNotNull();
        // payload reference rides in the header (conditionally present for data-bearing)...
        assertThat(stored.headers().payloadRef()).isEqualTo("ref-secret-payload");
        assertThat(stored.headers().carriesPayloadRef()).isTrue();
        // ...and NEVER in the body (the body is a routing descriptor only — §6.2②).
        assertThat(stored.routingDescriptor()).doesNotContain("ref-secret-payload");
    }

    @Test
    void produce_control_only_omits_payload_ref_header() {
        InMemoryBroker broker = broker(route -> Optional.of("topic-" + route.tenantScope()));
        ForwardingOutboxRecord record = record("tenant-a", "msg-1", null);

        broker.produce(record, 1_000L);

        BrokerOutboundMessage stored = broker.outboundMessage("tenant-a", "msg-1");
        assertThat(stored.headers().payloadRef()).isNull();
        assertThat(stored.headers().carriesPayloadRef()).isFalse();
    }

    // ===== produce: routeHandle resolved via resolver, never unwrapped (HD4) =====

    @Test
    void produce_resolves_topic_via_resolver_without_unwrapping_route_handle() {
        // The resolver is the sanctioned seam; the broker must publish to the topic the resolver
        // returns, not by unwrapping routeHandle.value() itself.
        InMemoryBroker broker = broker(route -> Optional.of("topic-from-resolver"));
        ForwardingOutboxRecord record = record("tenant-a", "msg-1", null);

        broker.produce(record, 1_000L);

        assertThat(broker.messageCount("topic-from-resolver")).isEqualTo(1);
    }

    @Test
    void produce_unresolvable_route_returns_non_retryable_route_not_found() {
        InMemoryBroker broker = broker(route -> Optional.empty());
        ForwardingOutboxRecord record = record("tenant-a", "msg-1", null);

        BrokerProduceOutcome outcome = broker.produce(record, 1_000L);

        assertThat(outcome.outcome()).isEqualTo(BrokerProduceOutcome.Outcome.ROUTE_NOT_FOUND);
        assertThat(outcome.failureCode()).isEqualTo(ForwardingFailureCode.ROUTE_NOT_FOUND);
        assertThat(outcome.failureCode().nonRetryable()).isTrue();
    }

    @Test
    void produce_when_broker_unavailable_returns_retryable_unavailable() {
        InMemoryBroker broker = broker(route -> Optional.of("topic-" + route.tenantScope()));
        broker.setUnavailable(true);
        ForwardingOutboxRecord record = record("tenant-a", "msg-1", null);

        BrokerProduceOutcome outcome = broker.produce(record, 1_000L);

        assertThat(outcome.outcome()).isEqualTo(BrokerProduceOutcome.Outcome.UNAVAILABLE);
        assertThat(outcome.failureCode()).isEqualTo(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
        assertThat(outcome.failureCode().retryable()).isTrue();
    }

    // ===== poll / commit: model B ack-after-consume =====

    @Test
    void poll_returns_next_message_for_consumer_and_tenant() {
        InMemoryBroker broker = broker(route -> Optional.of("topic-" + route.tenantScope()));
        broker.produce(record("tenant-a", "msg-1", "ref-1"), 1_000L);

        Optional<BrokerInboundMessage> polled = broker.poll("consumer-a", "tenant-a", 2_000L);

        assertThat(polled).isPresent();
        BrokerInboundMessage m = polled.orElseThrow();
        assertThat(m.messageId()).isEqualTo("msg-1");
        assertThat(m.tenantId()).isEqualTo("tenant-a");
        assertThat(m.sourceServiceId()).isEqualTo("source-svc");
        assertThat(m.targetServiceId()).isEqualTo("target-svc");
        assertThat(m.consumerServiceId()).isEqualTo("consumer-a");
        assertThat(m.payloadRef()).isEqualTo("ref-1");
    }

    @Test
    void poll_cross_tenant_message_is_never_returned() {
        InMemoryBroker broker = broker(route -> Optional.of("shared-topic"));
        broker.produce(record("tenant-a", "msg-1", null), 1_000L);

        // A receiver polls its own tenant; a message whose header tenantId differs is never returned.
        Optional<BrokerInboundMessage> polled = broker.poll("consumer-b", "tenant-b", 2_000L);

        assertThat(polled).isEmpty();
    }

    @Test
    void commit_advances_offset_so_message_is_not_redelivered() {
        InMemoryBroker broker = broker(route -> Optional.of("topic-" + route.tenantScope()));
        broker.produce(record("tenant-a", "msg-1", null), 1_000L);
        BrokerInboundMessage m = broker.poll("consumer-a", "tenant-a", 2_000L).orElseThrow();

        broker.commit(m);

        assertThat(broker.poll("consumer-a", "tenant-a", 3_000L)).isEmpty();
    }

    @Test
    void poll_without_commit_redelivers_the_same_message_at_least_once() {
        InMemoryBroker broker = broker(route -> Optional.of("topic-" + route.tenantScope()));
        broker.produce(record("tenant-a", "msg-1", null), 1_000L);

        BrokerInboundMessage first = broker.poll("consumer-a", "tenant-a", 2_000L).orElseThrow();
        // no commit, no reject — broker must redeliver
        BrokerInboundMessage second = broker.poll("consumer-a", "tenant-a", 3_000L).orElseThrow();

        assertThat(second.messageId()).isEqualTo(first.messageId());
        assertThat(second.tenantId()).isEqualTo(first.tenantId());
    }

    @Test
    void reject_does_not_commit_and_records_code_for_observability() {
        InMemoryBroker broker = broker(route -> Optional.of("topic-" + route.tenantScope()));
        broker.produce(record("tenant-a", "msg-1", null), 1_000L);
        BrokerInboundMessage m = broker.poll("consumer-a", "tenant-a", 2_000L).orElseThrow();

        broker.reject(m, ForwardingFailureCode.TENANT_MISMATCH);

        // reject does not advance the offset → redelivered
        assertThat(broker.poll("consumer-a", "tenant-a", 3_000L)).isPresent();
        assertThat(broker.lastRejectCode("msg-1")).isEqualTo(ForwardingFailureCode.TENANT_MISMATCH);
    }

    // ===== consumer-group isolation (L3) =====

    @Test
    void consumer_groups_maintain_independent_offsets() {
        InMemoryBroker broker = broker(route -> Optional.of("topic-" + route.tenantScope()));
        broker.produce(record("tenant-a", "msg-1", null), 1_000L);

        // consumer-a commits; consumer-b (a different consumer-group) still sees the message.
        BrokerInboundMessage forA = broker.poll("consumer-a", "tenant-a", 2_000L).orElseThrow();
        broker.commit(forA);

        Optional<BrokerInboundMessage> forB = broker.poll("consumer-b", "tenant-a", 3_000L);
        assertThat(forB).isPresent();
        assertThat(forB.orElseThrow().messageId()).isEqualTo("msg-1");
        assertThat(forB.orElseThrow().consumerServiceId()).isEqualTo("consumer-b");
    }

    // ===== record / outcome invariants =====

    @Test
    void produce_outcome_accepted_must_not_carry_a_failure_code() {
        assertThatThrownBy(() -> new BrokerProduceOutcome(BrokerProduceOutcome.Outcome.ACCEPTED,
                ForwardingFailureCode.ROUTE_NOT_FOUND))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(BrokerProduceOutcome.accepted().failureCode()).isNull();
    }

    @Test
    void produce_outcome_unavailable_requires_a_retryable_code() {
        assertThatThrownBy(() -> BrokerProduceOutcome.unavailable(ForwardingFailureCode.ROUTE_NOT_FOUND))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrokerProduceOutcome.unavailable(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void produce_outcome_route_not_found_requires_a_non_retryable_code() {
        assertThatThrownBy(() -> BrokerProduceOutcome.routeNotFound(ForwardingFailureCode.RECEIVER_UNAVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrokerProduceOutcome.routeNotFound(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void message_headers_require_mandatory_routing_metadata() {
        assertThatThrownBy(() -> new BrokerMessageHeaders(null, "m", "s", "t", null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BrokerMessageHeaders(" ", "m", "s", "t", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrokerMessageHeaders("tenant", "m", "s", "t", " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // FEAT-013: correlationId is nullable but rejects a blank value.
        assertThatThrownBy(() -> new BrokerMessageHeaders("tenant", "m", "s", "t", null, " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    void inbound_message_requires_consumer_service_id_and_routing_metadata() {
        assertThatThrownBy(() -> new BrokerInboundMessage("tenant", "m", "s", "t", null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BrokerInboundMessage("tenant", "m", "s", "t", " ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // FEAT-013: correlationId is nullable but rejects a blank value.
        assertThatThrownBy(() -> new BrokerInboundMessage("tenant", "m", "s", "t", "c", null, " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    // ===== correlationId propagation (FEAT-013 §4.2: gateway matches responses by correlationId) =====

    @Test
    void produce_then_poll_propagates_correlation_id_record_to_headers_to_inbound() {
        InMemoryBroker broker = broker(route -> Optional.of("topic-" + route.tenantScope()));
        // record() fixture mirrors correlationId="corr-"+messageId from the envelope; produce builds
        // headers from the record; poll mirrors headers→inbound. The gateway (S2) reads inbound.correlationId().
        broker.produce(record("tenant-a", "msg-corr", "ref-1"), 1_000L);

        BrokerInboundMessage m = broker.poll("consumer-a", "tenant-a", 2_000L).orElseThrow();

        assertThat(m.correlationId()).isEqualTo("corr-msg-corr");
        assertThat(m.eventType()).isEqualTo(AgentBusEventType.CLIENT_INVOCATION_REQUESTED);
        // and the stored outbound carries the same correlationId + eventType in its headers
        assertThat(broker.outboundMessage("tenant-a", "msg-corr").headers().correlationId())
                .isEqualTo("corr-msg-corr");
        assertThat(broker.outboundMessage("tenant-a", "msg-corr").headers().eventType())
                .isEqualTo(AgentBusEventType.CLIENT_INVOCATION_REQUESTED);
    }

    @Test
    void produce_control_only_carries_correlation_id_in_header() {
        // a CONTROL_ONLY record (payloadRef=null) still carries its correlationId — correlation is
        // routing metadata, independent of the payloadRef data-reference path (§6.2 ②).
        InMemoryBroker broker = broker(route -> Optional.of("topic-" + route.tenantScope()));
        broker.produce(record("tenant-a", "msg-corr-ctrl", null), 1_000L);

        BrokerOutboundMessage stored = broker.outboundMessage("tenant-a", "msg-corr-ctrl");
        assertThat(stored.headers().correlationId()).isEqualTo("corr-msg-corr-ctrl");
        assertThat(stored.headers().eventType()).isEqualTo(AgentBusEventType.CLIENT_INVOCATION_REQUESTED);
    }

    // ===== fixtures =====

    private InMemoryBroker broker(ForwardingEndpointResolver resolver) {
        return new InMemoryBroker(resolver);
    }

    private ForwardingOutboxRecord record(String tenantId, String messageId, String payloadRef) {
        return new ForwardingOutboxRecord(
                tenantId,
                new ForwardingMessageId(messageId),
                "source-svc",
                "target-svc",
                new ForwardingRouteHandle("route-for-" + tenantId, tenantId),
                payloadRef,
                ForwardingStatus.Outbox.PENDING,
                0,
                0L,
                0L,
                0L,
                null,
                null,
                "corr-" + messageId,    // FEAT-013 correlationId (mirrors envelope; non-null so propagation is testable)
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED);  // FEAT-013/014 eventType (mirrors envelope; non-null so propagation is testable)
    }
}
