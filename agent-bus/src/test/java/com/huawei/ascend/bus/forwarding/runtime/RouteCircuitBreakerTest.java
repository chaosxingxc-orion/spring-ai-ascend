package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RouteCircuitBreaker} — the three-state machine in
 * isolation (Stage 16, slice 1). The worker-integration behaviour (a breaker
 * wired into {@link ForwardingDispatcherWorker}) is covered by
 * {@code AgentBusForwardingRuntimeContractTest} (Stage 16, slice 2).
 *
 * <p>A controllable {@link EpochClock} ({@code long[] now}) drives the cooldown
 * judgement deterministically. {@code failureThreshold = 3}, {@code cooldown =
 * 1000ms} throughout.
 */
class RouteCircuitBreakerTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final ForwardingRouteHandle ROUTE_A =
            new ForwardingRouteHandle("route-a", "tenant-a");
    private static final ForwardingRouteHandle ROUTE_B =
            new ForwardingRouteHandle("route-b", "tenant-b");

    /** Breaker with threshold 3, cooldown 1000ms, reading the mutable clock. */
    private static RouteCircuitBreaker breaker(long[] now) {
        return new RouteCircuitBreaker(3, 1_000L, () -> now[0]);
    }

    private static ForwardingDeliveryResult retry() {
        return ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE);
    }

    /** Drive a fresh route to OPEN with {@code threshold} consecutive failures. */
    private static void trip(RouteCircuitBreaker b, ForwardingRouteHandle route) {
        for (int i = 0; i < 3; i++) {
            b.allowsDelivery(route);
            b.recordOutcome(route, retry());
        }
    }

    @Test
    void opens_after_failure_threshold_consecutive_failures() {
        long[] now = {T0};
        RouteCircuitBreaker b = breaker(now);
        assertThat(b.stateOf(ROUTE_A)).isEqualTo(RouteCircuitBreaker.State.CLOSED);
        for (int i = 0; i < 3; i++) {
            assertThat(b.allowsDelivery(ROUTE_A)).isTrue();
            b.recordOutcome(ROUTE_A, retry());
        }
        assertThat(b.stateOf(ROUTE_A))
                .as("three consecutive retryable failures trip the breaker (threshold=3)")
                .isEqualTo(RouteCircuitBreaker.State.OPEN);
    }

    @Test
    void under_threshold_stays_closed() {
        long[] now = {T0};
        RouteCircuitBreaker b = breaker(now);
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, retry());
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, retry());
        assertThat(b.stateOf(ROUTE_A))
                .as("two failures (under threshold 3) keep the breaker CLOSED")
                .isEqualTo(RouteCircuitBreaker.State.CLOSED);
    }

    @Test
    void open_blocks_delivery_until_cooldown_elapses_then_half_open() {
        long[] now = {T0};
        RouteCircuitBreaker b = breaker(now);
        trip(b, ROUTE_A); // OPEN at T0
        now[0] = T0 + 500;
        assertThat(b.allowsDelivery(ROUTE_A))
                .as("before cooldown an OPEN route blocks delivery")
                .isFalse();
        assertThat(b.stateOf(ROUTE_A)).isEqualTo(RouteCircuitBreaker.State.OPEN);
        now[0] = T0 + 1_000;
        assertThat(b.allowsDelivery(ROUTE_A))
                .as("at cooldown the breaker half-opens and allows one probe")
                .isTrue();
        assertThat(b.stateOf(ROUTE_A)).isEqualTo(RouteCircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void half_open_probe_success_closes_and_resets_count() {
        long[] now = {T0};
        RouteCircuitBreaker b = breaker(now);
        trip(b, ROUTE_A);
        now[0] = T0 + 1_000;
        assertThat(b.allowsDelivery(ROUTE_A)).isTrue(); // HALF_OPEN probe
        b.recordOutcome(ROUTE_A, ForwardingDeliveryResult.acked());
        assertThat(b.stateOf(ROUTE_A)).isEqualTo(RouteCircuitBreaker.State.CLOSED);
        // count was reset: two fresh failures stay under threshold
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, retry());
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, retry());
        assertThat(b.stateOf(ROUTE_A)).isEqualTo(RouteCircuitBreaker.State.CLOSED);
    }

    @Test
    void half_open_probe_failure_reopens_and_refreshes_cooldown() {
        long[] now = {T0};
        RouteCircuitBreaker b = breaker(now);
        trip(b, ROUTE_A); // OPEN at T0
        now[0] = T0 + 1_000;
        assertThat(b.allowsDelivery(ROUTE_A)).isTrue(); // HALF_OPEN probe
        b.recordOutcome(ROUTE_A, retry()); // probe failed
        assertThat(b.stateOf(ROUTE_A)).isEqualTo(RouteCircuitBreaker.State.OPEN);
        // cooldown clocked from the probe failure (T0+1000), not the original open (T0)
        now[0] = T0 + 1_500;
        assertThat(b.allowsDelivery(ROUTE_A))
                .as("500ms since the probe failure is under the 1000ms cooldown")
                .isFalse();
        now[0] = T0 + 2_000;
        assertThat(b.allowsDelivery(ROUTE_A))
                .as("1000ms since the probe failure re-half-opens")
                .isTrue();
    }

    @Test
    void half_open_allows_only_one_probe_at_a_time() {
        long[] now = {T0};
        RouteCircuitBreaker b = breaker(now);
        trip(b, ROUTE_A);
        now[0] = T0 + 1_000;
        assertThat(b.allowsDelivery(ROUTE_A)).isTrue(); // first probe in flight
        assertThat(b.stateOf(ROUTE_A)).isEqualTo(RouteCircuitBreaker.State.HALF_OPEN);
        assertThat(b.allowsDelivery(ROUTE_A))
                .as("a second delivery while the probe is in flight is blocked")
                .isFalse();
        assertThat(b.allowsDelivery(ROUTE_A)).isFalse();
        // the probe resolving clears the marker; the next cooldown allows a fresh probe
        b.recordOutcome(ROUTE_A, retry()); // re-opens at T0+1000
        now[0] = T0 + 2_000;
        assertThat(b.allowsDelivery(ROUTE_A)).isTrue();
    }

    @Test
    void ack_resets_consecutive_failure_count_in_closed() {
        long[] now = {T0};
        RouteCircuitBreaker b = breaker(now);
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, retry());
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, retry());
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, ForwardingDeliveryResult.acked()); // resets count to 0
        // two more failures stay under threshold (count restarted)
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, retry());
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, retry());
        assertThat(b.stateOf(ROUTE_A))
                .as("a success reset the count, so two later failures do not trip")
                .isEqualTo(RouteCircuitBreaker.State.CLOSED);
    }

    @Test
    void dlq_and_expired_outcomes_are_ignored() {
        long[] now = {T0};
        RouteCircuitBreaker b = breaker(now);
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, retry());
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, retry());
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A,
                ForwardingDeliveryResult.dlq(ForwardingFailureCode.ROUTE_NOT_FOUND));
        assertThat(b.stateOf(ROUTE_A))
                .as("a non-retryable DLQ is a config error, not a route failure")
                .isEqualTo(RouteCircuitBreaker.State.CLOSED);
        b.allowsDelivery(ROUTE_A);
        b.recordOutcome(ROUTE_A, ForwardingDeliveryResult.expired());
        assertThat(b.stateOf(ROUTE_A))
                .as("an EXPIRED outcome is the message's own deadline, not a route failure")
                .isEqualTo(RouteCircuitBreaker.State.CLOSED);
    }

    @Test
    void per_route_state_is_independent() {
        long[] now = {T0};
        RouteCircuitBreaker b = breaker(now);
        trip(b, ROUTE_A); // route-a OPEN
        assertThat(b.stateOf(ROUTE_A)).isEqualTo(RouteCircuitBreaker.State.OPEN);
        assertThat(b.stateOf(ROUTE_B))
                .as("route-b is untouched by route-a failures")
                .isEqualTo(RouteCircuitBreaker.State.CLOSED);
        assertThat(b.allowsDelivery(ROUTE_B)).isTrue();
        b.recordOutcome(ROUTE_B, retry());
        assertThat(b.stateOf(ROUTE_B)).isEqualTo(RouteCircuitBreaker.State.CLOSED);
    }

    @Test
    void constructor_rejects_invalid_arguments() {
        assertThatThrownBy(() -> new RouteCircuitBreaker(0, 1_000L, () -> T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RouteCircuitBreaker(3, 0L, () -> T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RouteCircuitBreaker(3, -1L, () -> T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RouteCircuitBreaker(3, 1_000L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void always_closed_never_blocks_and_ignores_outcomes() {
        ForwardingCircuitBreaker noOp = ForwardingCircuitBreaker.ALWAYS_CLOSED;
        assertThat(noOp.allowsDelivery(ROUTE_A)).isTrue();
        noOp.recordOutcome(ROUTE_A, retry()); // must not throw
        noOp.recordOutcome(ROUTE_A, ForwardingDeliveryResult.acked());
        assertThat(noOp.allowsDelivery(ROUTE_A)).isTrue();
    }
}
