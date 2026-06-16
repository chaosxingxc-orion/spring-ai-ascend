package com.huawei.ascend.collab.core;

import java.util.function.IntUnaryOperator;

/**
 * Tunables for the coordinator's <b>backpressure</b> and <b>token-economy</b> policy.
 *
 * <p>Defaults ({@link #defaults()}) preserve the simplest behavior — no backoff, no
 * dispatch budget, no result dedupe — so the eval harness stays deterministic. A
 * production deployment turns the guards on:
 *
 * <ul>
 *   <li><b>backoff</b> — wait between reclaim retries so a failing/slow agent is not
 *       hammered (negative feedback); a no-op {@code attempt -> 0} by default.</li>
 *   <li><b>maxDispatches</b> — token budget: a hard cap on the number of (re)dispatches
 *       across a whole batch. Each dispatch drives a real LLM agent, so unbounded
 *       retry/redispatch directly multiplies token spend; once the budget is spent the
 *       remaining tasks fail fast instead of each burning {@code maxAttempts}. {@code <=0}
 *       = unlimited.</li>
 *   <li><b>maxConcurrency</b> — hard ceiling on in-flight tasks in {@code runConcurrent}
 *       (bounds memory and downstream load regardless of the {@code parallelism} argument).
 *       {@code <=0} = use the parallelism argument.</li>
 *   <li><b>dedupeResults</b> — reuse a COMPLETED result for an identical
 *       {@code (capability, payload)} sub-task within the batch instead of re-dispatching,
 *       so duplicate work in a fan-out costs zero extra tokens. Guaranteed for sequential
 *       {@code run(...)}; best-effort under {@code runConcurrent(...)} (the cache is written at
 *       completion, so identical tasks already in flight each dispatch). See the
 *       {@code Coordinator} class doc "Dedupe scope".</li>
 *   <li><b>circuitFailureThreshold / circuitCooldownMs</b> — failure-aware routing for a
 *       fleet: after this many consecutive failures a worker is skipped for the cooldown,
 *       shedding load onto healthy peers instead of hammering a dead node. {@code <=0} =
 *       disabled.</li>
 * </ul>
 */
public record CoordinatorConfig(
        IntUnaryOperator backoffMs,
        int maxDispatches,
        int maxConcurrency,
        boolean dedupeResults,
        int circuitFailureThreshold,
        long circuitCooldownMs) {

    public CoordinatorConfig {
        if (backoffMs == null) {
            backoffMs = attempt -> 0;
        }
    }

    public static CoordinatorConfig defaults() {
        return new CoordinatorConfig(attempt -> 0, 0, 0, false, 0, 0);
    }

    public CoordinatorConfig withBackoff(IntUnaryOperator backoff) {
        return new CoordinatorConfig(backoff, maxDispatches, maxConcurrency, dedupeResults,
                circuitFailureThreshold, circuitCooldownMs);
    }

    public CoordinatorConfig withMaxDispatches(int n) {
        return new CoordinatorConfig(backoffMs, n, maxConcurrency, dedupeResults,
                circuitFailureThreshold, circuitCooldownMs);
    }

    public CoordinatorConfig withMaxConcurrency(int n) {
        return new CoordinatorConfig(backoffMs, maxDispatches, n, dedupeResults,
                circuitFailureThreshold, circuitCooldownMs);
    }

    public CoordinatorConfig withDedupe(boolean dedupe) {
        return new CoordinatorConfig(backoffMs, maxDispatches, maxConcurrency, dedupe,
                circuitFailureThreshold, circuitCooldownMs);
    }

    public CoordinatorConfig withCircuitBreaker(int failureThreshold, long cooldownMs) {
        return new CoordinatorConfig(backoffMs, maxDispatches, maxConcurrency, dedupeResults,
                failureThreshold, cooldownMs);
    }

    /** Exponential backoff {@code base * 2^(attempt-1)} capped at {@code capMs}. */
    public static IntUnaryOperator exponentialBackoff(int baseMs, int capMs) {
        return attempt -> {
            int shift = Math.max(0, Math.min(attempt - 1, 20));
            long ms = (long) baseMs << shift;
            return (int) Math.min(capMs, ms);
        };
    }
}
