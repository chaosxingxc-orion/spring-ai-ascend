package com.huawei.ascend.engine.planner.spi;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        if (planId.isBlank()) {
            throw new IllegalArgumentException("planId must be non-blank");
        }
        validateDependencies(steps, dependencies);
    }

    private static void validateDependencies(List<PlanStep> steps, Map<String, List<String>> dependencies) {
        Set<String> stepIds = new HashSet<>();
        for (PlanStep step : steps) {
            if (!stepIds.add(step.stepId())) {
                throw new IllegalArgumentException("duplicate stepId: " + step.stepId());
            }
        }
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            String stepId = entry.getKey();
            if (stepId == null || !stepIds.contains(stepId)) {
                throw new IllegalArgumentException("unknown dependency stepId: " + stepId);
            }
            for (String prerequisite : entry.getValue()) {
                if (prerequisite == null || !stepIds.contains(prerequisite)) {
                    throw new IllegalArgumentException("unknown dependency prerequisite: " + prerequisite);
                }
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String stepId : stepIds) {
            detectCycle(stepId, dependencies, visiting, visited);
        }
    }

    private static void detectCycle(
            String stepId,
            Map<String, List<String>> dependencies,
            Set<String> visiting,
            Set<String> visited) {
        if (visited.contains(stepId)) {
            return;
        }
        if (!visiting.add(stepId)) {
            throw new IllegalArgumentException("dependency cycle detected at stepId: " + stepId);
        }
        for (String prerequisite : dependencies.getOrDefault(stepId, List.of())) {
            detectCycle(prerequisite, dependencies, visiting, visited);
        }
        visiting.remove(stepId);
        visited.add(stepId);
    }
}
