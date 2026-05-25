package com.huawei.ascend.middleware.skill.spi;

import java.util.Map;
import java.util.Objects;

/**
 * One {@link Skill} execution request.
 *
 * <p>Authority: ADR-0127.
 *
 * @param tenantId      owning tenant (Rule R-C.c).
 * @param skillKey      target skill (for skills shared across multiple
 *                      {@link Skill} instances behind a delegate, this
 *                      lets the impl route).
 * @param inputs        argument map; never null.
 * @param hookContext   opaque context propagated through
 *                      {@code HookDispatcher} (traceId, runId, ...).
 *                      Never null, may be empty.
 */
public record SkillInvocation(
        String tenantId,
        String skillKey,
        Map<String, Object> inputs,
        Map<String, Object> hookContext) {

    public SkillInvocation {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(skillKey, "skillKey");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(hookContext, "hookContext");
        inputs = Map.copyOf(inputs);
        hookContext = Map.copyOf(hookContext);
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank (Rule R-C.c)");
        }
        if (skillKey.isBlank()) {
            throw new IllegalArgumentException("skillKey must be non-blank");
        }
    }
}
