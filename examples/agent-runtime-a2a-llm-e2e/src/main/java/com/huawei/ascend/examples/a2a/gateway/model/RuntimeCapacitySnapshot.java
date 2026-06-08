package com.huawei.ascend.examples.a2a.gateway.model;

import java.time.Instant;

public record RuntimeCapacitySnapshot(
        int runningTasks,
        int queuedTasks,
        int maxConcurrentTasks,
        int llmInFlight,
        int llmQueueDepth,
        int llmMaxConcurrency,
        long p50FirstTokenMs,
        long p95FirstTokenMs,
        long p99FirstTokenMs,
        int recent429Count,
        int recentTimeoutCount,
        double estimatedLoad,
        Instant observedAt) {

    public RuntimeCapacitySnapshot {
        requireNonNegative(runningTasks, "runningTasks");
        requireNonNegative(queuedTasks, "queuedTasks");
        requireNonNegative(maxConcurrentTasks, "maxConcurrentTasks");
        requireNonNegative(llmInFlight, "llmInFlight");
        requireNonNegative(llmQueueDepth, "llmQueueDepth");
        requireNonNegative(llmMaxConcurrency, "llmMaxConcurrency");
        requireNonNegative(p50FirstTokenMs, "p50FirstTokenMs");
        requireNonNegative(p95FirstTokenMs, "p95FirstTokenMs");
        requireNonNegative(p99FirstTokenMs, "p99FirstTokenMs");
        requireNonNegative(recent429Count, "recent429Count");
        requireNonNegative(recentTimeoutCount, "recentTimeoutCount");
        if (Double.isNaN(estimatedLoad) || Double.isInfinite(estimatedLoad) || estimatedLoad < 0.0) {
            throw new IllegalArgumentException("estimatedLoad must be a finite non-negative value");
        }
        observedAt = observedAt == null ? Instant.EPOCH : observedAt;
    }

    public static RuntimeCapacitySnapshot empty() {
        return new RuntimeCapacitySnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, Instant.EPOCH);
    }

    public boolean atCapacity() {
        return taskCapacityReached() || llmCapacityReached() || estimatedLoad >= 1.0;
    }

    public double routeScore() {
        double taskLoad = maxConcurrentTasks <= 0 ? 0.0 : (double) (runningTasks + queuedTasks) / maxConcurrentTasks;
        double llmLoad = llmMaxConcurrency <= 0 ? 0.0 : (double) (llmInFlight + llmQueueDepth) / llmMaxConcurrency;
        return Math.max(Math.max(taskLoad, llmLoad), estimatedLoad);
    }

    private boolean taskCapacityReached() {
        return maxConcurrentTasks > 0 && runningTasks + queuedTasks >= maxConcurrentTasks;
    }

    private boolean llmCapacityReached() {
        return llmMaxConcurrency > 0 && llmInFlight + llmQueueDepth >= llmMaxConcurrency;
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
