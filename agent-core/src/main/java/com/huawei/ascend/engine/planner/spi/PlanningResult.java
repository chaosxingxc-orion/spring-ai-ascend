package com.huawei.ascend.engine.planner.spi;

import java.util.List;
import java.util.Objects;

/**
 * Planner output: a plan, an error, or an infeasibility report.
 *
 * <p>Authority: ADR-0126.
 */
public sealed interface PlanningResult
        permits PlanningResult.PlanningSuccess,
                PlanningResult.PlanningError,
                PlanningResult.PlanningInfeasible {

    record PlanningSuccess(Plan plan, PlanningRationale rationale) implements PlanningResult {
        public PlanningSuccess {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(rationale, "rationale");
        }
    }

    record PlanningError(String reason, String detail) implements PlanningResult {
        public PlanningError {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * @param unmetConstraints budget / skill / memory constraints the
     *                         planner could not satisfy.
     */
    record PlanningInfeasible(String reason, List<String> unmetConstraints)
            implements PlanningResult {
        public PlanningInfeasible {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(unmetConstraints, "unmetConstraints");
            unmetConstraints = List.copyOf(unmetConstraints);
        }
    }
}
