package com.huawei.ascend.middleware.skill.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Opaque suspension state captured by a long-running skill.
 *
 * <p>Authority: ADR-0127. Consumed by the W2 async orchestrator's
 * {@code SuspendSignal} machinery; serialized via the platform
 * Checkpointer.
 *
 * @param resumeToken provider-defined token sufficient to resume.
 * @param payload     opaque serialised state.
 */
public record SkillSuspensionState(String resumeToken, Map<String, Object> payload) {
    public SkillSuspensionState {
        Objects.requireNonNull(resumeToken, "resumeToken");
        Objects.requireNonNull(payload, "payload");
        payload = Map.copyOf(payload);
    }
}
