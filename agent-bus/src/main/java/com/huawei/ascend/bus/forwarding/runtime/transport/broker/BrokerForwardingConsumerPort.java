package com.huawei.ascend.bus.forwarding.runtime.transport.broker;

import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;

import java.util.Optional;

/**
 * Receiver-side broker SPI: subscribe once at startup, then poll (param-less)
 * for inbound messages and commit / reject them (Stage 26 T4 hybrid + decision
 * packet agent-bus-broker-filtering-spi-completion §3 "after").
 *
 * <p><b>Subscribe lifecycle (D4/D5).</b> {@link #subscribe} registers this
 * consumer's group ({@code consumerServiceId} — the broker consumer-group),
 * the route to consume (resolved to a topic by the adapter's
 * {@code ForwardingEndpointResolver}, never unwrapped — HD4), and the
 * {@link DeliveryFilter} (broker-agnostic named-property criteria — D3). The
 * broker consumer object lives entirely inside the adapter; callers never touch
 * {@code org.apache.rocketmq}. A real adapter may be called multiple times to
 * accumulate subscriptions across routes (one consumer-group, multiple topics);
 * {@link #poll} then returns the next message matching any subscribed filter.
 *
 * <p><b>Poll (param-less — D4).</b> {@link #poll} takes only the poll instant:
 * the consumer's group, route, and filter were fixed at subscribe time. The
 * implementation returns the next uncommitted message matching the subscribed
 * filter; a message whose properties do not match is never returned (the
 * receiver never sees other runtimes' traffic). The receiver processes it, then
 * {@link #commit commits} (model B ack-after-consume — at-least-once) or
 * {@link #reject rejects} on failure (not committed → redelivered).
 *
 * <p><b>Capability bit (D8).</b> {@link #supportsBrokerSidePropertyFilter}
 * exposes whether this adapter filters broker-side (RocketMQ SQL92) — correctness
 * is unaffected (the receiver only ever receives filter-matching messages either
 * way); the bit is a perf/observability surface so operators know whether the
 * broker pre-filters or the adapter degrades to client-side (Kafka / Redis
 * Streams cannot filter broker-side — D7).
 *
 * <p>Broker product concepts (topic / partition / offset / consumer-group /
 * filter expression) never escape this port — {@code consumerServiceId} is the
 * broker consumer-group materialised as a plain string, {@link BrokerInboundMessage}
 * exposes no offset, and {@link DeliveryFilter} is structured criteria (never a
 * SQL string — the {@code bySql} lives only in the RocketMQ adapter, D3).
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/
 * agent-bus-broker-filtering-spi-completion-decision.md} §3 (D3/D4/D5/D8) — delta
 * on {@code agent-bus-forwarding-runtime-transport-decision.md} (Stage 25 §4/§10).
 */
// scope: forwarding transport.broker — receiver SPI; subscribe-once + param-less poll (model B, at-least-once)
public interface BrokerForwardingConsumerPort {

    /**
     * Register this consumer's group, route, and delivery filter. Called once at
     * startup (before any {@link #poll}); a real adapter may be called multiple
     * times to accumulate subscriptions across routes on the one consumer-group.
     *
     * @param consumerServiceId the broker consumer-group identifier (inbox dedup key)
     * @param route             the opaque route handle (adapter resolves to a topic; HD4)
     * @param filter            the broker-agnostic named-property criteria (D3)
     */
    void subscribe(String consumerServiceId, ForwardingRouteHandle route, DeliveryFilter filter);

    /**
     * Poll the next uncommitted inbound message matching the subscribed filter.
     * Returns empty when no matching message is available.
     *
     * @param nowMillisEpoch the poll instant
     * @return the next message, or empty
     */
    Optional<BrokerInboundMessage> poll(long nowMillisEpoch);

    /**
     * Commit a polled message — the receiver processed it successfully (model B
     * ack-after-consume). The message will not be redelivered to this consumer-group.
     *
     * @param message the polled message to commit (carries its in-flight consumerServiceId)
     */
    void commit(BrokerInboundMessage message);

    /**
     * Reject a polled message — the receiver could not process it. The message is NOT
     * committed, so the broker redelivers it (at-least-once); the failure code is
     * recorded for observability. Broker native retry/DLX is OFF (agent-bus retry
     * policy, Stage 14, leads).
     *
     * @param message the polled message to reject (carries its in-flight consumerServiceId)
     * @param code    the failure code (non-null)
     */
    void reject(BrokerInboundMessage message, ForwardingFailureCode code);

    /**
     * Shut down the underlying broker consumer (release the consumer-group's
     * broker-side resources). Idempotent.
     */
    void close();

    /**
     * Whether this adapter filters broker-side by the subscribed properties
     * (D8). Correctness is unaffected (callers only ever receive filter-matching
     * messages); the bit surfaces perf characteristics — {@code false} means the
     * adapter degrades to client-side filtering (Kafka / Redis Streams).
     *
     * @return true if the broker pre-filters by the subscribed properties
     */
    boolean supportsBrokerSidePropertyFilter();
}
