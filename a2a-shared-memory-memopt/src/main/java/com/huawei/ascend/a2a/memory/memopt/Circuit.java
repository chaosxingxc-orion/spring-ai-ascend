package com.huawei.ascend.a2a.memory.memopt;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * A small fail-open circuit guarding the MemOpt HTTP backend. Memory is a side
 * service that must never drag down the agent's main path: after
 * {@code failureThreshold} consecutive failures the circuit opens for {@code openMs},
 * during which calls short-circuit (no network round-trip); a success closes it.
 */
final class Circuit {

    private final int failureThreshold;
    private final long openMs;
    private final LongSupplier clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();

    Circuit(int failureThreshold, long openMs, LongSupplier clock) {
        this.failureThreshold = failureThreshold;
        this.openMs = openMs;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    boolean isOpen() {
        long until = openUntil.get();
        return until != 0 && clock.getAsLong() < until;
    }

    void onSuccess() {
        consecutiveFailures.set(0);
        openUntil.set(0);
    }

    void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntil.set(clock.getAsLong() + openMs);
        }
    }
}
