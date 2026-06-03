package com.huawei.ascend.runtime.engine.planner.spi;

import java.util.Objects;

/**
 * Loop annotation in a {@link Plan}.
 *
 * <p>Authority: ADR-0126.
 *
 * @param entryStepId          loop entry step.
 * @param terminationCondition expression evaluated each iteration.
 * @param maxIterations        cap to guarantee termination.
 */
public record LoopAnnotation(String entryStepId, String terminationCondition,
                             int maxIterations) {
    public LoopAnnotation {
        Objects.requireNonNull(entryStepId, "entryStepId");
        Objects.requireNonNull(terminationCondition, "terminationCondition");
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be > 0");
        }
    }
}
