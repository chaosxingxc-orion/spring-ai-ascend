package com.huawei.ascend.middleware.skill.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Result of one {@link Skill#execute(SkillInvocation)} call.
 *
 * <p>Authority: ADR-0127.
 */
public sealed interface SkillResult
        permits SkillResult.SkillSuccess,
                SkillResult.SkillError,
                SkillResult.SkillSuspended {

    record SkillSuccess(Map<String, Object> outputs) implements SkillResult {
        public SkillSuccess {
            Objects.requireNonNull(outputs, "outputs");
            outputs = Map.copyOf(outputs);
        }
    }

    /**
     * @param reason machine-readable code (e.g. {@code "invalid_argument"},
     *               {@code "provider_unavailable"}).
     * @param detail human-readable detail; never null.
     */
    record SkillError(String reason, String detail) implements SkillResult {
        public SkillError {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /** Skill suspended itself; resume via {@code SuspendSignal} machinery. */
    record SkillSuspended(SkillSuspensionState state) implements SkillResult {
        public SkillSuspended {
            Objects.requireNonNull(state, "state");
        }
    }
}
