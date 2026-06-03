package com.huawei.ascend.engine.planner.spi;

import java.time.Duration;
import java.util.Objects;

/**
 * Budget constraints the {@link Planner} must respect.
 *
 * <p>Authority: ADR-0126.
 *
 * @param maxSteps       cap on plan size; MUST be &gt; 0.
 * @param maxWallClock   cap on planner+executor wall-clock; never null.
 * @param maxCostUnits   cap in platform cost-attribution units (LLM
 *                       tokens + skill invocations).
 */
public record PlanningBudget(int maxSteps, Duration maxWallClock, double maxCostUnits) {
    public PlanningBudget {
        Objects.requireNonNull(maxWallClock, "maxWallClock");
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be > 0");
        }
        if (!Double.isFinite(maxCostUnits) || maxCostUnits < 0.0) {
            throw new IllegalArgumentException("maxCostUnits must be finite and >= 0");
        }
    }
}
