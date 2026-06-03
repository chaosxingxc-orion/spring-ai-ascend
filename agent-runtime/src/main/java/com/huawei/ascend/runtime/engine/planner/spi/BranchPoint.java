package com.huawei.ascend.runtime.engine.planner.spi;

import java.util.Objects;

/**
 * Conditional branch in a {@link Plan}.
 *
 * <p>Authority: ADR-0126.
 *
 * @param atStepId  the step whose result triggers the branch.
 * @param condition expression string evaluated against the step's
 *                  result; planner-defined dialect.
 * @param truePath  next stepId when condition holds.
 * @param falsePath next stepId when condition does not hold.
 */
public record BranchPoint(String atStepId, String condition,
                          String truePath, String falsePath) {
    public BranchPoint {
        Objects.requireNonNull(atStepId, "atStepId");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(truePath, "truePath");
        Objects.requireNonNull(falsePath, "falsePath");
    }
}
