package com.huawei.ascend.collab.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The per-worker circuit opens after N consecutive failures and recovers after cooldown / on success. */
class CircuitBreakerTest {

    @Test
    void opensAfterThresholdAndRecoversAfterCooldown() {
        AtomicLong now = new AtomicLong(0);
        CircuitBreaker cb = new CircuitBreaker(2, 100, now::get);

        cb.recordFailure("w");
        assertFalse(cb.isOpen("w"), "one failure does not trip");
        cb.recordFailure("w");
        assertTrue(cb.isOpen("w"), "two consecutive failures trip the breaker");

        now.set(50);
        assertTrue(cb.isOpen("w"), "still open inside the cooldown window");
        now.set(101);
        assertFalse(cb.isOpen("w"), "closed again after cooldown elapses");
    }

    @Test
    void successResetsTheFailureCount() {
        AtomicLong now = new AtomicLong(0);
        CircuitBreaker cb = new CircuitBreaker(2, 100, now::get);

        cb.recordFailure("w");
        cb.recordSuccess("w");
        cb.recordFailure("w");
        assertFalse(cb.isOpen("w"), "a success in between resets the consecutive-failure count");
    }

    @Test
    void breakerIsPerWorker() {
        AtomicLong now = new AtomicLong(0);
        CircuitBreaker cb = new CircuitBreaker(1, 100, now::get);

        cb.recordFailure("bad");
        assertTrue(cb.isOpen("bad"));
        assertFalse(cb.isOpen("good"), "an unrelated worker is unaffected");
    }
}
