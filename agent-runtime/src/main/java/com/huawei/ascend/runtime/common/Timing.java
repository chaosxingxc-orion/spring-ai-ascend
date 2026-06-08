package com.huawei.ascend.runtime.common;

/**
 * Elapsed-time helper for runtime observability logging.
 *
 * <p>Several components measure how long an operation took by capturing
 * {@link System#nanoTime()} at the start and reporting the delta in milliseconds
 * when they log completion. Centralising the conversion keeps the unit
 * (nanoseconds in, milliseconds out) consistent across every such log site.
 */
public final class Timing {

    private Timing() {
    }

    /**
     * Milliseconds elapsed since {@code startedNanos}, which must be an earlier
     * {@link System#nanoTime()} reading.
     */
    public static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
