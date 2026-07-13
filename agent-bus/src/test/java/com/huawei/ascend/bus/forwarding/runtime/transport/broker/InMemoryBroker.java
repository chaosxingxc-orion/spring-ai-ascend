package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory test double for the broker SPI ({@link BrokerForwardingRelayPort} +
 * {@link BrokerForwardingConsumerPort}) — NON-PRODUCTION.
 *
 * <p>Simulates broker semantics in plain JDK, mirroring how
 * {@code InMemoryForwardingOutbox} stands in for the JDBC outbox: the injected
 * {@link ForwardingEndpointResolver} maps the opaque routeHandle to a topic
 * (topic-per-tenant is the resolver's concern, not the broker's), produce appends
 * a {@link BrokerOutboundMessage} to an in-memory queue (a single ordered partition
 * per topic; a global sequence orders cross-topic poll), poll returns the next
 * uncommitted message for the consumer-group whose header tenantId matches, commit
 * advances the consumer-group offset (model B ack-after-consume), and at-least-once
 * redelivery is exercised by polling again without committing.
 *
 * <p>No broker client, no network, no scheduler — it is a deterministic harness for
 * the Stage 26 governance invariants (payloadRef-in-header, routeHandle opaque,
 * L2 tenant reject, consumer-group isolation, redelivery). A real RocketMQ adapter
 * (Stage 27+) is the production target; this double deliberately stores the
 * {@link BrokerOutboundMessage} it builds so contract tests can introspect that the
 * payload reference rides in the header, never in the body.
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/
 * agent-bus-forwarding-runtime-transport-decision.md} (Stage 25 §10 guardrails,
 * Stage 26 broker SPI scaffold).
 */
// non-production — test fixture only; real broker adapter is Stage 27+
public final class InMemoryBroker implements BrokerForwardingRelayPort, BrokerForwardingConsumerPort {

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

    // ===== BrokerForwardingConsumerPort =====

    @Override
    public synchronized Optional<BrokerInboundMessage> poll(String consumerServiceId, String tenantId,
                                                            long nowMillisEpoch) {
        requireNonBlank(consumerServiceId, "consumerServiceId");
        requireNonBlank(tenantId, "tenantId");
        QueueEntry picked = null;
        // Scan every topic for this consumer-group's next uncommitted, tenant-matching message;
        // pick the globally oldest (sequence) so cross-topic poll is deterministic. A message whose
        // header tenantId differs from the poll tenant is never returned (L2 header-tenant-check).
        for (Map.Entry<String, List<QueueEntry>> e : queues.entrySet()) {
            List<QueueEntry> q = e.getValue();
            int from = offsets.getOrDefault(consumerGroupKey(consumerServiceId, e.getKey()), 0);
            for (int i = from; i < q.size(); i++) {
                BrokerMessageHeaders h = q.get(i).message.headers();
                if (!h.tenantId().equals(tenantId)) {
                    continue; // cross-tenant: never returned (rejected, not committed)
                }
                if (picked == null || q.get(i).sequence < picked.sequence) {
                    picked = q.get(i);
                }
                break; // first uncommitted in this queue is the queue's candidate (sequence is monotonic)
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
        Location loc = locations.get(locationKey(message.tenantId(), message.messageId()));
        if (loc == null) {
            return; // unknown / already purged — nothing to advance
        }
        String key = consumerGroupKey(message.consumerServiceId(), loc.topic());
        int advancedTo = loc.index() + 1;
        int current = offsets.getOrDefault(key, 0);
        if (advancedTo > current) {
            offsets.put(key, advancedTo);
        }
    }

    @Override
    public synchronized void reject(BrokerInboundMessage message, ForwardingFailureCode code) {
        Objects.requireNonNull(message, "message is required");
        Objects.requireNonNull(code, "code is required");
        // NOT committed → redelivered (at-least-once). The code is recorded for observability only.
        rejections.put(message.messageId(), code);
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
