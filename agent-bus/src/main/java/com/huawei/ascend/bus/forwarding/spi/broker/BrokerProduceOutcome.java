package com.huawei.ascend.bus.forwarding.spi.broker;

import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;

import java.util.Objects;

/**
 * Outcome of a single broker <em>produce</em> attempt, returned by
 * {@link BrokerForwardingRelayPort#produce} (Stage 26, T4 hybrid relay).
 *
 * <p><b>Deliberately NOT a {@code ForwardingDeliveryResult}.</b> A broker produce
 * is fire-and-forget: {@link Outcome#ACCEPTED} means the broker accepted the
 * message, NOT that the receiver processed it. The terminal ack (outbox ACKED)
 * arrives later via the model-B reverse-ack channel once the receiver consumes
 * and commits (Stage 27 AWAITING_ACK state machine). Conflating produce-accepted
 * with delivery-acked would lose the at-least-once guarantee. So the relay SPI
 * speaks {@code BrokerProduceOutcome}, and Stage 27 decides how the relay adapter
 * composes with {@code ForwardingDeliveryPort} / the worker.
 *
 * <p>Three outcomes:
 * <ul>
 *   <li>{@link Outcome#ACCEPTED} → broker accepted the message (no failure code;
 *       the outbox record stays DISPATCHING / AWAITING_ACK until the reverse ack).</li>
 *   <li>{@link Outcome#UNAVAILABLE} → broker transiently unreachable / rejected
 *       (retryable failure code; the relay leaves the record for retry under the
 *       agent-bus retry policy — broker native retry is OFF, Stage 14 policy leads).</li>
 *   <li>{@link Outcome#ROUTE_NOT_FOUND} → the {@code ForwardingEndpointResolver}
 *       returned empty for the route handle (non-retryable; a route that cannot
 *       be mapped to a broker topic is a registry / configuration problem).</li>
 * </ul>
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/
 * agent-bus-forwarding-runtime-transport-decision.md} (Stage 25 adopted-t4,
 * Stage 26 broker SPI scaffold).
 */
// scope: forwarding transport.broker — broker produce outcome; not a terminal delivery result
public record BrokerProduceOutcome(Outcome outcome, ForwardingFailureCode failureCode) {

    public BrokerProduceOutcome {
        Objects.requireNonNull(outcome, "outcome is required");
        switch (outcome) {
            case ACCEPTED -> {
                if (failureCode != null) {
                    throw new IllegalArgumentException(
                            "ACCEPTED produce outcome must not carry a failureCode");
                }
            }
            case UNAVAILABLE -> {
                if (failureCode == null) {
                    throw new IllegalArgumentException(
                            "UNAVAILABLE produce outcome requires a retryable failureCode");
                }
                if (!failureCode.retryable()) {
                    throw new IllegalArgumentException(
                            "UNAVAILABLE produce outcome requires a retryable failureCode; "
                            + failureCode + " is not retryable");
                }
            }
            case ROUTE_NOT_FOUND -> {
                if (failureCode == null) {
                    throw new IllegalArgumentException(
                            "ROUTE_NOT_FOUND produce outcome requires a non-retryable failureCode");
                }
                if (!failureCode.nonRetryable()) {
                    throw new IllegalArgumentException(
                            "ROUTE_NOT_FOUND produce outcome requires a non-retryable failureCode; "
                            + failureCode + " is not non-retryable");
                }
            }
        }
    }

    /** Broker produce outcome — ACCEPTED is not a terminal delivery ack (model B). */
    public enum Outcome {
        ACCEPTED, UNAVAILABLE, ROUTE_NOT_FOUND
    }

    /** Broker accepted the message (fire-and-forget; the reverse ack comes later). */
    public static BrokerProduceOutcome accepted() {
        return new BrokerProduceOutcome(Outcome.ACCEPTED, null);
    }

    /**
     * Broker transiently unavailable / rejected the produce — retryable. The code
     * MUST be {@link ForwardingFailureCode#retryable() retryable}; the agent-bus
     * retry policy (Stage 14) owns when to retry (broker native retry is OFF).
     */
    public static BrokerProduceOutcome unavailable(ForwardingFailureCode failureCode) {
        return new BrokerProduceOutcome(Outcome.UNAVAILABLE, failureCode);
    }

    /**
     * Route handle could not be mapped to a broker topic (resolver returned empty)
     * — non-retryable. The code MUST be {@link ForwardingFailureCode#nonRetryable()
     * non-retryable}.
     */
    public static BrokerProduceOutcome routeNotFound(ForwardingFailureCode failureCode) {
        return new BrokerProduceOutcome(Outcome.ROUTE_NOT_FOUND, failureCode);
    }
}
