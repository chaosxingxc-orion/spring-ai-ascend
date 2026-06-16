package com.huawei.ascend.collab.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Per-worker circuit breaker: after {@code failureThreshold} consecutive failures a worker
 * is "opened" (skipped by routing) for {@code cooldownMs}; a success closes it again. In a
 * fleet this stops the coordinator from hammering a dead/struggling node and lets the load
 * shed onto healthy peers — failure-aware routing, not blind retry.
 *
 * <p>Thread-safe: state is per-worker and updated atomically, so it is correct under
 * {@code runConcurrent}.
 */
public final class CircuitBreaker {

    private final int failureThreshold;
    private final long cooldownMs;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public CircuitBreaker(int failureThreshold, long cooldownMs, LongSupplier clock) {
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    private static final class State {
        final AtomicInteger consecutiveFailures = new AtomicInteger();
        final AtomicLong openUntil = new AtomicLong(0);
    }

    /** True while the worker is in its cooldown window after tripping. */
    public boolean isOpen(String workerId) {
        State s = states.get(workerId);
        return s != null && clock.getAsLong() < s.openUntil.get();
    }

    public void recordSuccess(String workerId) {
        State s = states.get(workerId);
        if (s != null) {
            s.consecutiveFailures.set(0);
            s.openUntil.set(0);
        }
    }

    public void recordFailure(String workerId) {
        State s = states.computeIfAbsent(workerId, k -> new State());
        if (s.consecutiveFailures.incrementAndGet() >= failureThreshold) {
            s.openUntil.set(clock.getAsLong() + cooldownMs);
        }
    }
}
