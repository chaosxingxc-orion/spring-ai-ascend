package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult.Outcome;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-route three-state circuit breaker for the C3 forwarding dispatcher
 * (Stage 16 — the real implementation behind {@link ForwardingCircuitBreaker},
 * wired into {@link ForwardingDispatcherWorker}).
 *
 * <p>State machine (one {@link RouteState} per opaque
 * {@link ForwardingRouteHandle#value()}):
 * <pre>
 *   CLOSED --(failureThreshold consecutive failures)--> OPEN --(cooldown)--> HALF_OPEN
 *   HALF_OPEN --(probe success)--> CLOSED ; HALF_OPEN --(probe failure)--> OPEN
 * </pre>
 * The worker calls {@link #allowsDelivery} before each delivery — an OPEN route
 * short-circuits (the record is skipped, left DISPATCHING, reclaimed on lease
 * expiry, consuming no attemptCount — exactly the existing skip paths). After
 * each delivery it calls {@link #recordOutcome} to advance the machine.
 *
 * <p><b>Outcome classification</b> is internal (the worker passes the raw
 * {@link ForwardingDeliveryResult} once and does not care how it is classified):
 * {@link Outcome#ACKED} is a success (resets the count / closes a probe);
 * {@link Outcome#RETRY_SCHEDULED} is a failure — a retryable code (timeout /
 * receiver-unavailable / backpressure) is a route-unhealthy signal — that
 * increments the count or re-opens; {@link Outcome#DLQ} /
 * {@link Outcome#EXPIRED} are ignored (a non-retryable failure is a config
 * error, a retryable-exhausted failure was already counted on its earlier
 * RETRY_SCHEDULED, and an EXPIRED record hit its own deadline). Note
 * {@link ForwardingDeliveryResult#retry} already rejects a non-retryable code
 * (Stage 9 classification), so a RETRY_SCHEDULED outcome is always a retryable
 * failure — the breaker never re-checks {@code failureCode.retryable()}.
 *
 * <p><b>HALF_OPEN single probe.</b> At most one in-flight probe is permitted per
 * route (the {@code probeInFlight} marker). The worker's call ordering makes
 * this marker leak-proof: {@code allowsDelivery} runs AFTER the lease-renewal
 * check (a renew failure skips before the breaker is touched) and BEFORE
 * delivery; {@code recordOutcome} runs AFTER delivery and BEFORE the state
 * mutation (a lease-guard exception in the mutation cannot strand a probe); and
 * the deliver-exception catch feeds a {@code RECEIVER_UNAVAILABLE} failure so a
 * probe that threw is still closed out. See Stage 16 plan §(4).
 *
 * <p>Plain JDK-portable type — no Spring, JDBC, broker or scheduler dependency
 * (forwarding purity, decision §6.1). Per-route state lives in process memory
 * (a {@link ConcurrentHashMap}); a process restart loses it (returns to CLOSED).
 * Cross-process sharing / persistence of breaker state is deferred.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md §17};
 * Stage 16 plan §3.
 */
public final class RouteCircuitBreaker implements ForwardingCircuitBreaker {

    private final int failureThreshold;
    private final long cooldownMillis;
    private final EpochClock clock;
    private final ConcurrentMap<String, RouteState> states = new ConcurrentHashMap<>();

    /**
     * @param failureThreshold consecutive retryable failures (in CLOSED) that
     *                         trip the breaker to OPEN ({@code >= 1})
     * @param cooldownMillis  how long an OPEN route stays open before a single
     *                        HALF_OPEN probe is allowed ({@code > 0})
     * @param clock           the wall clock the cooldown judgement reads
     *                        (same source as the worker's lease-renewal clock,
     *                        MI11-001)
     */
    public RouteCircuitBreaker(int failureThreshold, long cooldownMillis, EpochClock clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        if (cooldownMillis <= 0) {
            throw new IllegalArgumentException("cooldownMillis must be > 0");
        }
        this.failureThreshold = failureThreshold;
        this.cooldownMillis = cooldownMillis;
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public boolean allowsDelivery(ForwardingRouteHandle routeHandle) {
        Objects.requireNonNull(routeHandle, "routeHandle is required");
        RouteState s = states.computeIfAbsent(routeHandle.value(), ignored -> new RouteState());
        synchronized (s) {
            switch (s.state) {
                case CLOSED:
                    return true;
                case OPEN:
                    if (clock.epochMillis() - s.openedAtMillisEpoch >= cooldownMillis) {
                        s.state = State.HALF_OPEN;
                        s.probeInFlight = true; // this call is the single allowed probe
                        return true;
                    }
                    return false;
                case HALF_OPEN:
                    if (!s.probeInFlight) {
                        s.probeInFlight = true; // allow one probe at a time
                        return true;
                    }
                    return false; // a probe is already in flight
                default:
                    throw new IllegalStateException("unexpected state: " + s.state);
            }
        }
    }

    @Override
    public void recordOutcome(ForwardingRouteHandle routeHandle, ForwardingDeliveryResult result) {
        Objects.requireNonNull(routeHandle, "routeHandle is required");
        Objects.requireNonNull(result, "result is required");
        RouteState s = states.get(routeHandle.value());
        if (s == null) {
            // No state means allowsDelivery was never called for this route; a real
            // delivery always follows an allow, so this is a defensive no-op.
            return;
        }
        boolean success = result.outcome() == Outcome.ACKED;
        boolean failure = result.outcome() == Outcome.RETRY_SCHEDULED;
        if (!success && !failure) {
            // DLQ / EXPIRED do not change breaker state.
            return;
        }
        long now = clock.epochMillis();
        synchronized (s) {
            if (s.state == State.HALF_OPEN) {
                s.probeInFlight = false; // the probe resolved either way
                if (success) {
                    s.state = State.CLOSED;
                    s.consecutiveFailures = 0;
                } else {
                    s.state = State.OPEN;
                    s.openedAtMillisEpoch = now;
                }
            } else if (s.state == State.CLOSED) {
                if (success) {
                    s.consecutiveFailures = 0;
                } else if (++s.consecutiveFailures >= failureThreshold) {
                    s.state = State.OPEN;
                    s.openedAtMillisEpoch = now;
                }
            }
            // OPEN: allowsDelivery returned false, so no delivery should record an
            // outcome here; ignore defensively (a straggler outcome from before the
            // open cannot flip state on its own).
        }
    }

    /**
     * The breaker's current state for a route (test observability only; never
     * read by production dispatch code, which uses only {@link #allowsDelivery}).
     * Public so the cross-package architecture / contract tests can assert state
     * transitions directly; the same-package unit test uses it too.
     */
    public State stateOf(ForwardingRouteHandle routeHandle) {
        RouteState s = states.get(routeHandle.value());
        return s == null ? State.CLOSED : s.state;
    }

    /** Breaker state (mirrors the classic three-state machine). */
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    /** Mutable per-route breaker state, guarded by synchronizing on the instance. */
    private static final class RouteState {
        State state = State.CLOSED;
        int consecutiveFailures = 0;
        long openedAtMillisEpoch = 0L;
        boolean probeInFlight = false;
    }
}
