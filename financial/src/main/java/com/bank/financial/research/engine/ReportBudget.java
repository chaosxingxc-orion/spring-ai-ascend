package com.bank.financial.research.engine;

/**
 * Hard budget for one report run — the resilience backstop that keeps a
 * multi-agent generation from running away in iterations, model calls, or
 * wall-clock time. The orchestrator checks every cap and stops early (producing
 * the best report so far) rather than looping unboundedly.
 *
 * @param maxCriticRounds  max writer↔critic revision cycles (the AutoGen loop cap)
 * @param targetWords      soft target for total report length (guides the writer)
 * @param maxModelCalls    hard ceiling on LLM generations across all agents
 * @param timeoutMs        wall-clock budget; 0 disables the time check (tests)
 */
public record ReportBudget(int maxCriticRounds, int targetWords, int maxModelCalls, long timeoutMs) {

    public ReportBudget {
        if (maxCriticRounds < 0) {
            throw new IllegalArgumentException("maxCriticRounds must be >= 0");
        }
        if (maxModelCalls < 1) {
            throw new IllegalArgumentException("maxModelCalls must be >= 1");
        }
    }

    /** Production default: up to 2 revision rounds, ~8k-word report, 40 model calls, 5-minute cap. */
    public static ReportBudget standard() {
        return new ReportBudget(2, 8000, 40, 5 * 60 * 1000L);
    }

    /** Deterministic test default: no wall-clock limit, generous call ceiling. */
    public static ReportBudget forTest() {
        return new ReportBudget(2, 4000, 100, 0L);
    }
}
