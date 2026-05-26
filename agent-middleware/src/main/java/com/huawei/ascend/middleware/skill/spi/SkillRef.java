package com.huawei.ascend.middleware.skill.spi;

import java.util.Objects;

/**
 * Opaque reference to a {@link Skill} by key. Used by
 * {@code Agent.toolBindings} and tool-call arguments in
 * {@link com.huawei.ascend.middleware.skill.spi.SkillInvocation}
 * cross-references.
 *
 * <p>Carries only {@code skillKey} — resolution happens through
 * the {@link SkillRegistry} at the call site.
 *
 * <p>Authority: ADR-0127.
 */
public record SkillRef(String skillKey) {
    public SkillRef {
        Objects.requireNonNull(skillKey, "skillKey");
        if (skillKey.isBlank()) {
            throw new IllegalArgumentException("skillKey must be non-blank");
        }
    }
}
