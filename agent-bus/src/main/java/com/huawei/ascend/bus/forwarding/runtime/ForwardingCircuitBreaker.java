package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;

/**
 * Per-route circuit-breaker seam for the C3 forwarding dispatcher.
 *
 * <p><b>Stage 14 — port reserved, not wired.</b> Stage 13 separated the
 * deliver-retry subitem (timing of a retryable failure — now owned by
 * {@link ForwardingRetryPolicy}) from circuit-breaking (per-route
 * failure-rate governance). Stage 14 shipped this port + the
 * {@link #ALWAYS_CLOSED} no-op but intentionally did NOT wire it into
 * {@link ForwardingDispatcherWorker}: a real breaker needs
 * per-{@link ForwardingRouteHandle} failure-rate state and its shape depends
 * on the transport model — a <b>push</b> model (T1 / T2) has the dispatcher
 * driving delivery, so it needs the breaker to actively short-circuit a
 * failing route; a <b>consumer-pull</b> model (T3) is inherently self-paced
 * (the receiver simply stops claiming), so an explicit breaker is largely
 * redundant. Wiring before that decision would bake in a transport assumption.
 *
 * <p><b>Stage 15 — the blocker fell.</b> The real delivery binding PoC chose
 * <b>T1</b> (synchronous wait-for-completion = dispatcher-push over sync RPC,
 * mirroring main's {@code A2aRemoteAgentOutboundAdapter}). T1 is a push model,
 * so the dispatcher drives delivery and needs the breaker to actively
 * short-circuit a failing route — the suspended precondition landed.
 *
 * <p><b>Stage 16 — wired into the worker.</b> A real three-state implementation
 * ({@link RouteCircuitBreaker}) now backs this port. Before each delivery the
 * worker calls {@link #allowsDelivery}; an OPEN route is short-circuited
 * (skipped, left DISPATCHING, reclaimed on lease expiry — like the existing
 * lease / deliver-exception skip paths, consuming no attemptCount). After each
 * delivery the worker calls {@link #recordOutcome} to feed the result back; the
 * breaker classifies it internally (ACKED → success, a retryable failure →
 * failure, DLQ / EXPIRED → ignored). The port stays transport-agnostic — it
 * consumes only {@link ForwardingRouteHandle} / {@link ForwardingDeliveryResult}
 * — so even if H2/H3 ultimately rules T3, the wired breaker does no harm (a
 * self-paced receiver already stops claiming, so the breaker rarely trips).
 *
 * <p>Plain JDK-portable type — no Spring, JDBC, broker or scheduler dependency
 * (forwarding purity, decision §6.1).
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md}
 * (deliver-retry-policy-subitem; circuit-breaker); Stage 16 plan §3.
 */
public interface ForwardingCircuitBreaker {

    /**
     * Whether delivery to the given route is currently permitted. A real
     * implementation tracks per-{@link ForwardingRouteHandle} failure rate and
     * returns {@code false} to short-circuit an OPEN route — the caller then
     * defers the record (skips, leaving it DISPATCHING) rather than delivering
     * into a known-failing route.
     *
     * @param routeHandle the opaque route (never unwrapped to a physical endpoint)
     */
    boolean allowsDelivery(ForwardingRouteHandle routeHandle);

    /**
     * Feed a delivery outcome back to the breaker so its per-route state machine
     * advances. The breaker classifies the result internally:
     * <ul>
     *   <li>{@link ForwardingDeliveryResult.Outcome#ACKED} — a success: resets
     *       the consecutive-failure count (CLOSED) or closes a probe
     *       (HALF_OPEN → CLOSED);</li>
     *   <li>{@link ForwardingDeliveryResult.Outcome#RETRY_SCHEDULED} — a
     *       retryable failure (timeout / receiver-unavailable / backpressure):
     *       increments the count (CLOSED → OPEN at the threshold) or re-opens
     *       (HALF_OPEN → OPEN);</li>
     *   <li>{@link ForwardingDeliveryResult.Outcome#DLQ} /
     *       {@link ForwardingDeliveryResult.Outcome#EXPIRED} — ignored: a
     *       non-retryable failure is a config error (not route overload) and a
     *       retryable-exhausted failure was already counted on its earlier
     *       RETRY_SCHEDULED; an EXPIRED record hit its own deadline.</li>
     * </ul>
     * The worker calls this once per delivery, after {@code deliver} returns
     * and before the state mutation — and also from the deliver-exception
     * catch (as a {@code RECEIVER_UNAVAILABLE} failure) so a HALF_OPEN probe
     * can never leak its in-flight marker.
     *
     * @param routeHandle the opaque route the outcome is for
     * @param result      the delivery result (never {@code null})
     */
    void recordOutcome(ForwardingRouteHandle routeHandle, ForwardingDeliveryResult result);

    /**
     * No-op breaker: the circuit is always closed, so delivery is never blocked
     * and outcomes are ignored. The default the worker uses when no per-route
     * breaker is injected.
     */
    ForwardingCircuitBreaker ALWAYS_CLOSED = new ForwardingCircuitBreaker() {
        @Override
        public boolean allowsDelivery(ForwardingRouteHandle routeHandle) {
            return true;
        }

        @Override
        public void recordOutcome(ForwardingRouteHandle routeHandle, ForwardingDeliveryResult result) {
            // no-op — the always-closed breaker tracks no per-route state
        }
    };
}
