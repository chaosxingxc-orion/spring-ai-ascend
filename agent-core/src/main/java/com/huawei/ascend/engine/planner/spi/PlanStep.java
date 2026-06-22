package com.huawei.ascend.engine.planner.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One step in a {@link Plan}.
 *
 * <p>Authority: ADR-0126.
 *
 * @param stepId         unique within plan.
 * @param displayName    human-readable label.
 * @param skillKey       identifier of the skill executing this step
 *                       (resolved by the platform skill-lookup mechanism).
 * @param inputs         argument map for the skill.
 * @param readMemoryRef  optional memory binding the step may read.
 * @param writeMemoryRef optional memory binding the step may write.
 * @param stepBudget     per-step budget cap.
 */
public record PlanStep(
        String stepId,
        String displayName,
        String skillKey,
        Map<String, Object> inputs,
        Optional<String> readMemoryRef,
        Optional<String> writeMemoryRef,
        StepBudget stepBudget) {

    public PlanStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(skillKey, "skillKey");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(readMemoryRef, "readMemoryRef");
        Objects.requireNonNull(writeMemoryRef, "writeMemoryRef");
        Objects.requireNonNull(stepBudget, "stepBudget");
        inputs = Map.copyOf(inputs);
        if (stepId.isBlank()) {
            throw new IllegalArgumentException("stepId must be non-blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must be non-blank");
        }
        if (skillKey.isBlank()) {
            throw new IllegalArgumentException("skillKey must be non-blank");
        }
    }
}
