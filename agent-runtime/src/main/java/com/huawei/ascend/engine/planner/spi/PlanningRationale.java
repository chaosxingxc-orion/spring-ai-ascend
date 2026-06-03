package com.huawei.ascend.engine.planner.spi;

import java.util.List;
import java.util.Objects;

/**
 * Why the planner produced this plan.
 *
 * <p>Authority: ADR-0126. Auditable record for review and replay.
 *
 * @param reasoningTrace        free-form reasoning narrative.
 * @param consideredAlternatives alternatives the planner evaluated
 *                              and rejected.
 */
public record PlanningRationale(String reasoningTrace,
                                List<String> consideredAlternatives) {
    public PlanningRationale {
        Objects.requireNonNull(reasoningTrace, "reasoningTrace");
        Objects.requireNonNull(consideredAlternatives, "consideredAlternatives");
        consideredAlternatives = List.copyOf(consideredAlternatives);
    }
}
