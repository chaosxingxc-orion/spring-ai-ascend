package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingConsumerPort;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingRelayPort;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerInboundMessage;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerProduceOutcome;
import com.huawei.ascend.bus.forwarding.spi.broker.DeliveryFilter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory test double for the broker relay ({@link BrokerForwardingRelayPort}) +
 * the factory for per-consumer {@link BrokerForwardingConsumerPort} doubles —
 * NON-PRODUCTION.
 *
 * <p>Simulates broker semantics in plain JDK, mirroring how
 * {@code InMemoryForwardingOutbox} stands in for the JDBC outbox: the injected
 * {@link ForwardingEndpointResolver} maps the opaque routeHandle to a topic
 * (topic-per-tenant is the resolver's concern, not the broker's), produce appends
 * a {@link BrokerOutboundMessage} to an in-memory queue (a single ordered
 * partition per topic; a global sequence orders cross-topic poll). Consumers are
 * obtained per consumer-group via {@link #consumerFor(String)} — each is a
 * {@link InMemoryBrokerConsumer} backed by this shared broker's queues; a
 * consumer subscribes with a {@link DeliveryFilter} and polls param-less,
 * receiving only messages whose properties match the subscribed filter (the
 * in-memory "broker" filters by the properties itself — the broker-side
 * equivalent, so {@link BrokerForwardingConsumerPort#supportsBrokerSidePropertyFilter}
 * is true). commit advances the consumer-group offset (model B ack-after-consume);
 * at-least-once redelivery is exercised by polling again without committing.
 *
 * <p><b>Per-consumer instances.</b> A single shared broker serves multiple
 * consumer-groups via separate {@code consumerFor} instances — that mirrors the
 * real adapter (one {@code DefaultLitePullConsumer} per consumer-group), which
 * the param-less {@code poll} requires: the consumer's group + filter were fixed
 * at subscribe time, so {@code poll} cannot direct across groups. Within one
 * consumer, {@code subscribe} may be called multiple times to accumulate
 * subscriptions across routes (multi-topic consume on one consumer-group, like
 * RocketMQ's multi-{@code subscribe}).
 *
 * <p>No broker client, no network, no scheduler — it is a deterministic harness
 * for the Stage 26 + §3 governance invariants (payloadRef-in-header, routeHandle
 * opaque, L2 tenant reject, consumer-group isolation, redelivery, filter
 * matching). A real RocketMQ adapter (decision §7 / Stage 28) is the production
 * target; this double deliberately stores the {@link BrokerOutboundMessage} it
 * builds so contract tests can introspect that the payload reference rides in the
 * header, never in the body.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/
 * agent-bus-broker-filtering-spi-completion-decision.md} (§3 after SPI +
 * Stage 25 §10 guardrails, Stage 26 broker SPI scaffold).
 */
// non-production — test fixture only; real broker adapter is decision §7 / Stage 28
public final class InMemoryBroker implements BrokerForwardingRelayPort {

    /** A queued broker message; its index in the topic list is the consumer-group offset. */
    private static final class QueueEntry {
        final long sequence;              // global append order, for cross-topic poll ordering
        final BrokerOutboundMessage message;

        QueueEntry(long sequence, BrokerOutboundMessage message) {
            this.sequence = sequence;
            this.message = message;
        }
    }

    /** Locates a polled message for commit / reject without leaking topic/offset on the message. */
    private record Location(String topic, int index) {}

    private final ForwardingEndpointResolver resolver;
    private final Map<String, List<QueueEntry>> queues = new LinkedHashMap<>();   // topic → append-only queue
    private final Map<String, Integer> offsets = new LinkedHashMap<>();           // consumerServiceId@topic → next read index
    private final Map<String, Location> locations = new LinkedHashMap<>();        // tenantId|messageId → location
    private final Map<String, ForwardingFailureCode> rejections = new LinkedHashMap<>(); // messageId → last reject code (observability)
    private final AtomicLong sequence = new AtomicLong();
    private volatile boolean unavailable = false;

    public InMemoryBroker(ForwardingEndpointResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver is required");
    }

    /** Test-only: force the broker into a transiently-unavailable state (simulates UNAVAILABLE produce). */
    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    /** Test-only introspection: the stored outbound message (so contracts can assert header vs body). */
    public synchronized BrokerOutboundMessage outboundMessage(String tenantId, String messageId) {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(messageId, "messageId");
        for (List<QueueEntry> q : queues.values()) {
            for (QueueEntry e : q) {
                BrokerMessageHeaders h = e.message.headers();
                if (h.tenantId().equals(tenantId) && h.messageId().equals(messageId)) {
                    return e.message;
                }
            }
        }
        return null;
    }

    /** Test-only introspection: number of messages produced to a topic. */
    public synchronized int messageCount(String topic) {
        requireNonBlank(topic, "topic");
        List<QueueEntry> q = queues.get(topic);
        return q == null ? 0 : q.size();
    }

    /** Test-only introspection: last recorded reject code for a message (null if none / not rejected). */
    public synchronized ForwardingFailureCode lastRejectCode(String messageId) {
        requireNonBlank(messageId, "messageId");
        return rejections.get(messageId);
    }

    /**
     * Obtain a per-consumer {@link BrokerForwardingConsumerPort} double bound to the
     * given consumer-group, backed by this shared broker's queues. Each consumer
     * subscribes with its own {@link DeliveryFilter}; offsets are tracked per
     * {@code consumerServiceId} so distinct consumers are isolated (L3).
     */
    public BrokerForwardingConsumerPort consumerFor(String consumerServiceId) {
        requireNonBlank(consumerServiceId, "consumerServiceId");
        return new InMemoryBrokerConsumer(this, consumerServiceId);
    }

    // ===== BrokerForwardingRelayPort =====

    @Override
    public synchronized BrokerProduceOutcome produce(ForwardingOutboxRecord record, long nowMillisEpoch) {
        Objects.requireNonNull(record, "record is required");
        // HD4: the adapter resolves routeHandle via the injected resolver, never reading routeHandle.value().
        Optional<String> topic = resolver.resolve(record.routeHandle());
        if (topic.isEmpty()) {
            return BrokerProduceOutcome.routeNotFound(ForwardingFailureCode.ROUTE_NOT_FOUND);
        }
        if (unavailable) {
            return BrokerProduceOutcome.unavailable(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
        }
        String t = topic.get();
        // §6.2②: body is a routing descriptor only — NEVER the payload body / token stream / Task state.
        // payloadRef rides as a header (conditionally), NOT in the descriptor.
        BrokerMessageHeaders headers = new BrokerMessageHeaders(
                record.tenantId(),
                record.messageId().value(),
                record.sourceServiceId(),
                record.targetServiceId(),
                record.payloadRef(),              // null for CONTROL_ONLY, non-null for DATA_BEARING
                record.correlationId(),           // FEAT-013 cross-hop correlation (mirrored from envelope)
                record.eventType());              // FEAT-013/014 event-type (mirrored from envelope)
        BrokerOutboundMessage outbound = new BrokerOutboundMessage(
                "target=" + record.targetServiceId(),   // routing descriptor only
                headers);
        List<QueueEntry> q = queues.computeIfAbsent(t, k -> new ArrayList<>());
        int index = q.size();
        q.add(new QueueEntry(sequence.incrementAndGet(), outbound));
        locations.put(locationKey(record.tenantId(), record.messageId().value()), new Location(t, index));
        return BrokerProduceOutcome.accepted();
    }

    // ===== per-consumer double (nested so it can touch the shared broker's private state) =====

    /**
     * Per-consumer {@link BrokerForwardingConsumerPort} double backed by the shared
     * {@link InMemoryBroker}'s queues. {@code subscribe} accumulates the consumer's
     * filters (multi-route, like RocketMQ's multi-subscribe); {@code poll} scans the
     * shared queues for this consumer-group's next uncommitted message matching ANY
     * subscribed filter, picking the globally oldest for deterministic cross-topic
     * ordering. {@code consumerServiceId} is materialised into the in-flight message
     * at poll time (ownership until commit/reject).
     */
    static final class InMemoryBrokerConsumer implements BrokerForwardingConsumerPort {
        private final InMemoryBroker broker;
        private final String consumerServiceId;
        private final List<DeliveryFilter> filters = new ArrayList<>();   // accumulated subscriptions

        InMemoryBrokerConsumer(InMemoryBroker broker, String consumerServiceId) {
            this.broker = Objects.requireNonNull(broker, "broker is required");
            requireNonBlank(consumerServiceId, "consumerServiceId");
            this.consumerServiceId = consumerServiceId;
        }

        @Override
        public void subscribe(String consumerServiceId, ForwardingRouteHandle route, DeliveryFilter filter) {
            // the consumer is bound to its consumerServiceId at construction (consumerFor); the param is
            // the same group the real adapter would set — accept + accumulate the filter. The route is
            // resolved by the real adapter; this double scans every topic, so the route is informational here.
            Objects.requireNonNull(consumerServiceId, "consumerServiceId is required");
            Objects.requireNonNull(route, "route is required");
            Objects.requireNonNull(filter, "filter is required");
            if (!this.consumerServiceId.equals(consumerServiceId)) {
                throw new IllegalArgumentException(
                        "consumerServiceId '" + consumerServiceId + "' does not match this consumer ('"
                                + this.consumerServiceId + "')");
            }
            filters.add(filter);
        }

        @Override
        public synchronized Optional<BrokerInboundMessage> poll(long nowMillisEpoch) {
            if (filters.isEmpty()) {
                throw new IllegalStateException(
                        "consumer '" + consumerServiceId + "' polled before subscribe");
            }
            QueueEntry picked = null;
            // Scan every topic for this consumer-group's next uncommitted message whose header properties
            // match ANY subscribed filter; pick the globally oldest (sequence) for deterministic ordering.
            for (Map.Entry<String, List<QueueEntry>> e : broker.queues.entrySet()) {
                List<QueueEntry> q = e.getValue();
                int from = broker.offsets.getOrDefault(consumerGroupKey(consumerServiceId, e.getKey()), 0);
                for (int i = from; i < q.size(); i++) {
                    BrokerMessageHeaders h = q.get(i).message.headers();
                    if (!matchesAnyFilter(h)) {
                        continue; // not targeted at this consumer — never returned
                    }
                    if (picked == null || q.get(i).sequence < picked.sequence) {
                        picked = q.get(i);
                    }
                    break; // first uncommitted matching in this queue is the queue's candidate (sequence monotonic)
                }
            }
            if (picked == null) {
                return Optional.empty();
            }
            BrokerMessageHeaders h = picked.message.headers();
            // consumerServiceId is materialised into the in-flight message at poll time (ownership until commit/reject).
            return Optional.of(new BrokerInboundMessage(
                    h.tenantId(),
                    h.messageId(),
                    h.sourceServiceId(),
                    h.targetServiceId(),
                    consumerServiceId,
                    h.payloadRef(),
                    h.correlationId(),               // FEAT-013 cross-hop correlation (mirrored from headers)
                    h.eventType()));                  // FEAT-013/014 event-type (mirrored from headers)
        }

        @Override
        public synchronized void commit(BrokerInboundMessage message) {
            Objects.requireNonNull(message, "message is required");
            Location loc = broker.locations.get(locationKey(message.tenantId(), message.messageId()));
            if (loc == null) {
                return; // unknown / already purged — nothing to advance
            }
            String key = consumerGroupKey(message.consumerServiceId(), loc.topic());
            int advancedTo = loc.index() + 1;
            int current = broker.offsets.getOrDefault(key, 0);
            if (advancedTo > current) {
                broker.offsets.put(key, advancedTo);
            }
        }

        @Override
        public synchronized void reject(BrokerInboundMessage message, ForwardingFailureCode code) {
            Objects.requireNonNull(message, "message is required");
            Objects.requireNonNull(code, "code is required");
            // NOT committed → redelivered (at-least-once). The code is recorded for observability only.
            broker.rejections.put(message.messageId(), code);
        }

        @Override
        public void close() {
            // in-memory: no broker-side consumer to shut down; idempotent no-op.
        }

        @Override
        public boolean supportsBrokerSidePropertyFilter() {
            // the in-memory "broker" filters by the subscribed properties itself — the broker-side
            // equivalent (D8). A client-side-only fallback adapter (Kafka/Redis) would return false.
            return true;
        }

        /** True if the message's header properties satisfy at least one accumulated filter (D3 matching). */
        private boolean matchesAnyFilter(BrokerMessageHeaders h) {
            for (DeliveryFilter f : filters) {
                if (matches(h, f)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matches(BrokerMessageHeaders h, DeliveryFilter f) {
            for (Map.Entry<String, String> criterion : f.requiredProperties().entrySet()) {
                String expected = criterion.getValue();
                String actual = headerProperty(h, criterion.getKey());
                if (actual == null || !actual.equals(expected)) {
                    return false; // a missing/non-matching property → filter excludes the message
                }
            }
            return true;
        }

        /** Map a DeliveryFilter property key to the message's header value (the known routing headers). */
        private static String headerProperty(BrokerMessageHeaders h, String key) {
            return switch (key) {
                case "tenantId" -> h.tenantId();
                case "targetServiceId" -> h.targetServiceId();
                case "sourceServiceId" -> h.sourceServiceId();
                case "messageId" -> h.messageId();
                case "correlationId" -> h.correlationId();
                default -> null; // unknown property key — the in-memory double carries only routing headers
            };
        }
    }

    // ===== internals =====

    private static String consumerGroupKey(String consumerServiceId, String topic) {
        return consumerServiceId + "@" + topic;
    }

    private static String locationKey(String tenantId, String messageId) {
        return tenantId + "|" + messageId;
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
