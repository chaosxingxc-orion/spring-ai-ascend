package com.huawei.ascend.a2a.memory.memopt;

import java.util.function.LongSupplier;

/**
 * A small fail-open circuit guarding the MemOpt HTTP backend. Memory is a side
 * service that must never drag down the agent's main path: after
 * {@code failureThreshold} consecutive failures the circuit OPENs for {@code openMs},
 * during which calls short-circuit (no network round-trip). When the window
 * elapses the circuit goes HALF_OPEN and lets exactly <b>one</b> probe through —
 * its success CLOSEs the circuit, its failure re-OPENs immediately. The single
 * probe stops a thundering herd of concurrent callers from all hitting a backend
 * that is likely still unhealthy the instant the window expires.
 *
 * <p>All state transitions are taken under the monitor so the
 * count→open→half-open→close machine is observed atomically; the per-call cost is
 * a single uncontended lock, negligible for a side-service guard.
 */
final class Circuit {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openMs;
    private final LongSupplier clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openUntil;

    Circuit(int failureThreshold, long openMs, LongSupplier clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openMs = Math.max(0, openMs);
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /**
     * Whether a caller may proceed with the real backend call. CLOSED always
     * admits; OPEN admits nothing until the window elapses, at which point the
     * first caller is promoted to the HALF_OPEN probe; HALF_OPEN admits no further
     * callers while a probe is outstanding. Every {@code true} return must be
     * paired with exactly one {@link #onSuccess()} / {@link #onFailure()}.
     */
    synchronized boolean tryAcquire() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (clock.getAsLong() >= openUntil) {
                    state = State.HALF_OPEN; // promote this caller to the lone probe
                    return true;
                }
                return false;
            case HALF_OPEN:
            default:
                return false; // a probe is already in flight
        }
    }

    /** True while the circuit is rejecting calls (OPEN window not yet elapsed). */
    synchronized boolean isOpen() {
        return state == State.OPEN && clock.getAsLong() < openUntil;
    }

    synchronized void onSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        openUntil = 0;
    }

    synchronized void onFailure() {
        if (state == State.HALF_OPEN) {
            open(); // probe failed → straight back to OPEN, fresh window
            return;
        }
        if (++consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    /** Caller holds the monitor. */
    private void open() {
        state = State.OPEN;
        openUntil = clock.getAsLong() + openMs;
        consecutiveFailures = failureThreshold; // stay tripped until a probe closes us
    }
}
