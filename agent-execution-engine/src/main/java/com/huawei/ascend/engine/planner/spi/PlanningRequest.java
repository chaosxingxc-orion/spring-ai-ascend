package com.huawei.ascend.engine.planner.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Planner input envelope.
 *
 * <p>Authority: ADR-0126.
 *
 * @param tenantId           owning tenant (Rule R-C.c).
 * @param goal               free-text user goal; never null.
 * @param context            run-context snapshot the planner may
 *                           consult; never null, may be empty.
 * @param availableSkillKeys identifiers of {@code Skill}s available
 *                           to the planner; never null, may be empty.
 * @param availableMemoryRefs identifiers of {@code MemoryStore}s
 *                            available; never null, may be empty.
 * @param budget             max steps / wall-clock / cost the
 *                           planner must respect.
 * @param strategy           hint for planner algorithm choice.
 */
public record PlanningRequest(
        String tenantId,
        String goal,
        Map<String, Object> context,
        List<String> availableSkillKeys,
        List<String> availableMemoryRefs,
        PlanningBudget budget,
        PlanningStrategy strategy) {

    public PlanningRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(availableSkillKeys, "availableSkillKeys");
        Objects.requireNonNull(availableMemoryRefs, "availableMemoryRefs");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(strategy, "strategy");
        context = Map.copyOf(context);
        availableSkillKeys = List.copyOf(availableSkillKeys);
        availableMemoryRefs = List.copyOf(availableMemoryRefs);
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank (Rule R-C.c)");
        }
        if (goal.isBlank()) {
            throw new IllegalArgumentException("goal must be non-blank");
        }
    }
}
