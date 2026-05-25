package com.huawei.ascend.engine.planner.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Planner output: a DAG of steps with dependencies, branch points,
 * and loop annotations.
 *
 * <p>Authority: ADR-0126.
 *
 * @param planId       stable id within (tenantId, planner).
 * @param steps        ordered list of steps; never null.
 * @param dependencies map stepId → prerequisite stepIds; never null.
 *                     Cycle-free per planner contract.
 * @param branches     conditional step selections; never null,
 *                     may be empty.
 * @param loops        loop annotations with termination conditions;
 *                     never null, may be empty.
 * @param metadata     planner-supplied extras; never null.
 */
public record Plan(
        String planId,
        List<PlanStep> steps,
        Map<String, List<String>> dependencies,
        List<BranchPoint> branches,
        List<LoopAnnotation> loops,
        Map<String, Object> metadata) {

    public Plan {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(branches, "branches");
        Objects.requireNonNull(loops, "loops");
        Objects.requireNonNull(metadata, "metadata");
        steps = List.copyOf(steps);
        dependencies = dependencies.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
        branches = List.copyOf(branches);
        loops = List.copyOf(loops);
        metadata = Map.copyOf(metadata);
    }
}
