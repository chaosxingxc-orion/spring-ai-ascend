package com.huawei.ascend.bus.forwarding.runtime.relay;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Pure dispatch loop that drives {@link RelayTick#runOnce} ticks from an injected
 * {@link TickSource} — the relay-scheduler primitive mirroring
 * {@code ForwardingDispatchLoop}. The loop owns no clock, no scheduler, no thread
 * pool, no transport (decision §6.1: no scheduler/timer in the bus). The caller
 * injects (a) when to run the next tick ({@link TickSource}) and (b) what to do
 * when a tick polled nothing ({@link RelayIdleStrategy}). Real cadence, threading,
 * idle backoff and fault isolation are the caller's concern.
 *
 * <p>Because the clock + worker are injected, the loop is fully deterministic and
 * unit-testable without any thread or sleep.
 *
 * <p>Authority: {@code docs/superpowers/specs/2026-07-15-relay-scheduler-design.md} §4.2;
 * mirrors {@code ForwardingDispatchLoop} (forwarding/runtime/ForwardingDispatchLoop.java).
 */
public final class RelayDispatchLoop {

    /** Supplies the instant of the next tick, or empty to stop the loop. Injected (no clock in the loop). */
    @FunctionalInterface
    public interface TickSource {
        OptionalLong nextTickMillisEpoch();
    }

    /** Reacts to an idle tick (one that polled nothing — all counts zero). The loop itself never sleeps. */
    @FunctionalInterface
    public interface RelayIdleStrategy {
        void onIdle(EventBusRelayWorker.RelayTickResult lastTick);
    }

    /** No-op idle strategy: never backs off. For fixed-delay drivers / tests. */
    public static final RelayIdleStrategy NO_BACKOFF = tick -> { };

    private final RelayTick worker;
    private final TickSource tickSource;
    private final RelayIdleStrategy idleStrategy;

    public RelayDispatchLoop(RelayTick worker, TickSource tickSource, RelayIdleStrategy idleStrategy) {
        this.worker = Objects.requireNonNull(worker, "worker is required");
        this.tickSource = Objects.requireNonNull(tickSource, "tickSource is required");
        this.idleStrategy = Objects.requireNonNull(idleStrategy, "idleStrategy is required");
    }

    /**
     * Run ticks until {@link TickSource} stops, aggregating the results.
     *
     * @param tenantId tenant scope applied to every tick
     * @param limit    max messages to relay per tick ({@code > 0})
     * @return the aggregate of all ticks run
     * @throws IllegalArgumentException if {@code tenantId} is blank or {@code limit <= 0}
     */
    public EventBusRelayWorker.RelayTickResult run(String tenantId, int limit) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        int relayed = 0;
        int dedupSuppressed = 0;
        int governanceRejected = 0;
        int skipped = 0;
        while (true) {
            OptionalLong next = tickSource.nextTickMillisEpoch();
            if (next.isEmpty()) {
                break;
            }
            long now = next.getAsLong();
            EventBusRelayWorker.RelayTickResult tick = worker.runOnce(tenantId, now, limit);
            relayed += tick.relayed();
            dedupSuppressed += tick.dedupSuppressed();
            governanceRejected += tick.governanceRejected();
            skipped += tick.skipped();
            if (isIdle(tick)) {
                idleStrategy.onIdle(tick);
            }
        }
        return new EventBusRelayWorker.RelayTickResult(relayed, dedupSuppressed, governanceRejected, skipped);
    }

    /** A tick that polled nothing — all four counts zero. */
    private static boolean isIdle(EventBusRelayWorker.RelayTickResult tick) {
        return tick.relayed() == 0 && tick.dedupSuppressed() == 0
                && tick.governanceRejected() == 0 && tick.skipped() == 0;
    }
}
