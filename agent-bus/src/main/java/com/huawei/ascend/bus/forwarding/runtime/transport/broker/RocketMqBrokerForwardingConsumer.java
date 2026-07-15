package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import com.huawei.ascend.bus.forwarding.runtime.transport.ForwardingEndpointResolver;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.client.consumer.MessageSelector;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RocketMQ concrete adapter for {@link BrokerForwardingConsumerPort} (FEAT-013/014, decision
 * packet agent-bus-broker-filtering-spi-completion §7 slice 2).
 *
 * <p>Subscribes a {@code DefaultLitePullConsumer} (group={@code consumerServiceId},
 * topic=resolver(route), filter={@code MessageSelector.bySql(sql92Expression(filter))}) and polls
 * param-less, committing per-message (model B ack-after-consume). The broker-agnostic
 * {@link DeliveryFilter} (D3) is translated to a RocketMQ SQL92 expression HERE — the SPI layer
 * never sees SQL (bySql confined to the adapter, mirroring how {@link RocketMqBrokerForwardingRelay}
 * owns the produce-side mapping).
 *
 * <p><b>Testability seam.</b> {@link MessagePollerFactory} (+ {@link MessagePoller}) isolate the
 * {@code DefaultLitePullConsumer} lifecycle so unit tests inject a fake (no live broker) — the same
 * seam pattern the relay uses ({@link RocketMqBrokerForwardingRelay.MessageSender} for produce).
 * The factory is group-bound lazily at {@link #subscribe}: the consumer-group arrives at subscribe,
 * not construction (§3 "subscribe 懒注册 DefaultLitePullConsumer（group=consumerServiceId）"), so it
 * cannot be pre-constructed like the relay's {@code DefaultMQProducer}. The prod
 * {@code defaultPollerFactory} (live {@code DefaultLitePullConsumer}) lands with the slice-3 IT
 * migration (§6 D4 hard proof) — the real-broker round-trip is an env-guarded IT, not a unit test.
 *
 * <p>Broker native retry is OFF — the agent-bus retry policy (Stage 14) leads; a polled message the
 * receiver rejects is NOT committed, so the broker redelivers it (at-least-once, model B).
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/
 * agent-bus-broker-filtering-spi-completion-decision.md} §7 / §3 (D3/D4/D5/D8/D14);
 * L2 feat-013 §5.2.
 */
// scope: forwarding transport.broker — concrete RocketMQ consumer adapter (SPI-licensed, ArchUnit-confined)
public final class RocketMqBrokerForwardingConsumer implements BrokerForwardingConsumerPort {

    /**
     * Seam that constructs the broker poller for a consumer-group (known at subscribe, not
     * construction — §3 lazy registration). Unit tests inject a fake returning a recording poller;
     * prod injects {@code defaultPollerFactory} (live {@code DefaultLitePullConsumer}, slice-3 IT).
     * Mirrors {@link RocketMqBrokerForwardingRelay.MessageSender} as the injectable broker surface.
     */
    @FunctionalInterface
    public interface MessagePollerFactory {
        MessagePoller pollerFor(String consumerGroup);
    }

    /**
     * Broker poller object (the {@code DefaultLitePullConsumer} surface). {@code subscribe}
     * registers a topic + the SQL92 expression (the prod poller wraps it in
     * {@code MessageSelector.bySql}; the broker filters broker-side, D8); {@code poll} returns the
     * next matching message (empty on timeout); {@code commit} acks (model B); {@code reject} does
     * NOT ack (redelivered); {@code close} shuts the consumer down.
     */
    public interface MessagePoller {
        void subscribe(String topic, String sql92Expression);

        Optional<MessageExt> poll(long timeoutMillis);

        void commit(MessageExt message);

        void reject(MessageExt message, ForwardingFailureCode code);

        void close();
    }

    private final ForwardingEndpointResolver resolver;
    private final MessagePollerFactory factory;
    private final long pollWaitMillis;
    private MessagePoller poller;                  // lazily created at first subscribe (group-bound)
    private String consumerServiceId;              // the consumer-group (set at subscribe)
    private final Map<String, MessageExt> inFlight = new ConcurrentHashMap<>(); // tenantId|messageId → ext

    public RocketMqBrokerForwardingConsumer(ForwardingEndpointResolver resolver,
                                            MessagePollerFactory factory, long pollWaitMillis) {
        this.resolver = Objects.requireNonNull(resolver, "resolver is required");
        this.factory = Objects.requireNonNull(factory, "factory is required");
        this.pollWaitMillis = pollWaitMillis;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Lazily creates the poller on the first call (group={@code consumerServiceId}); subsequent
     * calls accumulate routes on the one consumer-group (RocketMQ multi-subscribe). The route is
     * resolved to a topic by the injected {@link ForwardingEndpointResolver} (HD4 — never unwrapped
     * here); the filter is translated to SQL92 by {@link #sql92Expression} (D3).
     */
    @Override
    public void subscribe(String consumerServiceId, ForwardingRouteHandle route, DeliveryFilter filter) {
        Objects.requireNonNull(consumerServiceId, "consumerServiceId is required");
        Objects.requireNonNull(route, "route is required");
        Objects.requireNonNull(filter, "filter is required");
        if (poller == null) {
            // §3 lazy registration: the DefaultLitePullConsumer is group-bound and the group arrives at
            // subscribe, so the poller is created HERE (once), not at construction.
            poller = factory.pollerFor(consumerServiceId);
            this.consumerServiceId = consumerServiceId;
        } else if (!this.consumerServiceId.equals(consumerServiceId)) {
            // multi-subscribe accumulates routes on the one consumer-group; a different group on the
            // same instance is a wiring error (the consumer-group is fixed at first subscribe).
            throw new IllegalArgumentException(
                    "consumerServiceId '" + consumerServiceId + "' does not match this consumer ('"
                            + this.consumerServiceId + "')");
        }
        String topic = resolver.resolve(route)
                .orElseThrow(() -> new IllegalStateException("unresolvable route: " + route.value()));
        poller.subscribe(topic, sql92Expression(filter));
    }

    @Override
    public Optional<BrokerInboundMessage> poll(long nowMillisEpoch) {
        if (poller == null) {
            // the consumer-group / filter were fixed at subscribe; poll before that is a wiring error
            // (mirrors InMemoryBrokerConsumer — fail fast rather than return a spurious empty).
            throw new IllegalStateException("polled before subscribe");
        }
        Optional<MessageExt> ext = poller.poll(pollWaitMillis);
        if (ext.isEmpty()) {
            return Optional.empty();
        }
        MessageExt m = ext.get();
        // track the in-flight ext so commit / reject can ack / nack the underlying message (model B).
        inFlight.put(inFlightKey(m), m);
        return Optional.of(toInbound(m));
    }

    /**
     * Map a polled {@link MessageExt} (routing user-properties) → broker-agnostic
     * {@link BrokerInboundMessage}; the polling {@code consumerServiceId} is materialised at poll
     * time (ownership until commit/reject), mirroring {@link InMemoryBroker} (T4 hybrid).
     */
    private BrokerInboundMessage toInbound(MessageExt m) {
        return new BrokerInboundMessage(
                m.getProperty("tenantId"),
                m.getProperty("messageId"),
                m.getProperty("sourceServiceId"),
                m.getProperty("targetServiceId"),
                consumerServiceId,
                m.getProperty("payloadRef"),
                m.getProperty("correlationId"),
                softEventType(m.getProperty("eventType")));
    }

    /** In-flight key — the inbox dedup key {@code (tenantId, messageId)} (a polled message is owned until commit/reject). */
    private static String inFlightKey(MessageExt m) {
        return m.getProperty("tenantId") + "|" + m.getProperty("messageId");
    }

    /** In-flight key for a {@link BrokerInboundMessage} (commit / reject look up the polled ext by the same dedup key). */
    private static String inFlightKey(BrokerInboundMessage m) {
        return m.tenantId() + "|" + m.messageId();
    }

    /** Tolerantly map an eventType user-property → {@link AgentBusEventType} (null / unknown → null, mirroring the IT's response-side consumer). */
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

    @Override
    public void commit(BrokerInboundMessage message) {
        Objects.requireNonNull(message, "message is required");
        // model B ack-after-consume: hand the polled ext to the poller (commitSync); drop it from
        // in-flight so a re-commit is a no-op (idempotent — already-acked messages stay acked).
        MessageExt ext = inFlight.remove(inFlightKey(message));
        if (ext != null) {
            poller.commit(ext);
        }
    }

    @Override
    public void reject(BrokerInboundMessage message, ForwardingFailureCode code) {
        Objects.requireNonNull(message, "message is required");
        Objects.requireNonNull(code, "code is required");
        // NOT committed → the broker redelivers (at-least-once, model B); the code is recorded for
        // observability only. Drop from in-flight so the redelivery re-polls as a fresh message.
        MessageExt ext = inFlight.remove(inFlightKey(message));
        if (ext != null) {
            poller.reject(ext, code);
        }
    }

    @Override
    public void close() {
        // the underlying consumer is shut down via the poller (idempotent — DefaultLitePullConsumer
        // shutdown is re-entrant); null-safe when close precedes subscribe (no poller yet).
        if (poller != null) {
            poller.close();
        }
    }

    @Override
    public boolean supportsBrokerSidePropertyFilter() {
        // RocketMQ SQL92 filters broker-side (D8) — the receiver only ever receives filter-matching
        // messages; the bit surfaces perf (a client-side-only fallback like Kafka would return false).
        return true;
    }

    // ===== pure mapping (slice 2, unit-tested) =====

    /**
     * Pure mapping: a broker-agnostic {@link DeliveryFilter} → RocketMQ SQL92 expression string
     * (the {@code MessageSelector.bySql} argument). Each {@code requiredProperties} entry becomes a
     * {@code key = 'value'} clause; clauses are AND-joined and keys sorted for a deterministic
     * expression (RocketMQ does not constrain clause order). Single quotes in values are doubled
     * ({@code '} → {@code ''}) per SQL92 string-literal escaping, so a value containing a quote
     * stays well-formed and matches literally. Extracted static + package-private so unit tests
     * pin the mapping without a broker.
     */
    static String sql92Expression(DeliveryFilter filter) {
        Objects.requireNonNull(filter, "filter is required");
        // sort keys for a deterministic expression (RocketMQ does not constrain clause order);
        // single quotes in values are doubled per SQL92 string-literal escaping.
        List<String> keys = new ArrayList<>(filter.requiredProperties().keySet());
        Collections.sort(keys);
        List<String> clauses = new ArrayList<>(keys.size());
        for (String key : keys) {
            String value = filter.requiredProperties().get(key);
            clauses.add(key + " = '" + value.replace("'", "''") + "'");
        }
        return String.join(" AND ", clauses);
    }

    // ===== prod poller (live DefaultLitePullConsumer) — §6 D4: real-broker IT verifies, not a unit =====

    /**
     * Production {@link MessagePollerFactory} backed by a live {@link DefaultLitePullConsumer}.
     * Per §3 lazy registration, the consumer is constructed when the factory is invoked at
     * {@link #subscribe} (group={@code consumerServiceId} is known then, not at adapter construction —
     * {@code DefaultLitePullConsumer} is group-bound in its constructor). The consumer is NOT started
     * here — {@link DefaultLitePuller#subscribe} registers the (topic, bySql) subscription and starts
     * the consumer on the first subscribe, so the FIRST subscription is registered BEFORE start (the
     * push-consumer lifecycle RocketMQ expects); subsequent subscribes (multi-topic accumulate) update
     * the subscription post-start (a rebalance picks them up). The real-broker round-trip is the
     * env-guarded IT (slice 3 / §6 D4) — no unit test (a live broker is not a unit). Mirrors
     * {@link RocketMqBrokerForwardingRelay#defaultSender}.
     *
     * @param nameserverAddr the RocketMQ nameserver address (e.g. {@code host:9876})
     * @return a factory that, given a consumer-group, constructs (not starts) a {@link DefaultLitePullConsumer}
     */
    public static MessagePollerFactory defaultPollerFactory(String nameserverAddr) {
        Objects.requireNonNull(nameserverAddr, "nameserverAddr is required");
        if (nameserverAddr.isBlank()) {
            throw new IllegalArgumentException("nameserverAddr must not be blank");
        }
        return consumerGroup -> {
            DefaultLitePullConsumer consumer = new DefaultLitePullConsumer(consumerGroup);
            consumer.setNamesrvAddr(nameserverAddr);
            return new DefaultLitePuller(consumer);
        };
    }

    /**
     * {@link MessagePoller} over a {@link DefaultLitePullConsumer}. {@code subscribe} wraps the SQL92
     * string in {@link MessageSelector#bySql} (D3 — bySql adapter-confined) and starts the consumer on
     * the first call (subscribe-before-start — the first subscription is registered before start);
     * {@code poll} blocks up to {@code timeoutMillis} for the next message (LitePull returns a batch —
     * drained one-at-a-time so the adapter's per-message poll contract holds); {@code commit} is
     * {@code commitSync} (model B ack-after-consume); {@code reject} does NOT commit (the broker
     * redelivers); {@code close} shuts the consumer down (idempotent).
     */
    static final class DefaultLitePuller implements MessagePoller {
        private final DefaultLitePullConsumer consumer;
        private final Queue<MessageExt> drained = new LinkedList<>(); // LitePull batch → one-at-a-time drain
        private volatile boolean started;

        DefaultLitePuller(DefaultLitePullConsumer consumer) {
            this.consumer = Objects.requireNonNull(consumer, "consumer is required");
        }

        @Override
        public void subscribe(String topic, String sql92Expression) {
            try {
                // register the subscription BEFORE start on the first call (the push-consumer lifecycle
                // RocketMQ expects; subscribe-after-start can miss the first rebalance → no queues assigned).
                consumer.subscribe(topic, MessageSelector.bySql(sql92Expression));
                if (!started) {
                    started = true;
                    consumer.start();
                }
            } catch (Exception e) {
                throw new IllegalStateException("DefaultLitePullConsumer.subscribe/start failed topic=" + topic, e);
            }
        }

        @Override
        public Optional<MessageExt> poll(long timeoutMillis) {
            if (drained.isEmpty()) {
                List<MessageExt> batch = consumer.poll(timeoutMillis); // blocks up to timeout; null-safe
                if (batch != null && !batch.isEmpty()) {
                    drained.addAll(batch);
                }
            }
            return Optional.ofNullable(drained.poll());
        }

        @Override
        public void commit(MessageExt message) {
            try {
                consumer.commitSync(); // acks polled-but-uncommitted messages (model B ack-after-consume)
            } catch (Exception e) {
                throw new IllegalStateException("DefaultLitePullConsumer.commitSync failed", e);
            }
        }

        @Override
        public void reject(MessageExt message, ForwardingFailureCode code) {
            // no native reject — do NOT commitSync → the broker redelivers (at-least-once); the code is
            // recorded by the caller (observability), not re-passed to the broker.
        }

        @Override
        public void close() {
            consumer.shutdown(); // idempotent
        }
    }
}
