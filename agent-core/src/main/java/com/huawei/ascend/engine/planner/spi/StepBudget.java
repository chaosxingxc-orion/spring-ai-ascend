package com.huawei.ascend.engine.planner.spi;

import java.time.Duration;
import java.util.Objects;

/**
 * Per-step budget cap.
 *
 * <p>Authority: ADR-0126.
 */
public record StepBudget(Duration maxWallClock, double maxCostUnits) {
    public StepBudget {
        Objects.requireNonNull(maxWallClock, "maxWallClock");
        if (!Double.isFinite(maxCostUnits) || maxCostUnits < 0.0) {
            throw new IllegalArgumentException("maxCostUnits must be finite and >= 0");
        }
    }
}
