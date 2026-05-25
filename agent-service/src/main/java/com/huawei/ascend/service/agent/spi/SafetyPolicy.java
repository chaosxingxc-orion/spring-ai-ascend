package com.huawei.ascend.service.agent.spi;

import java.util.Objects;
import java.util.Set;

/**
 * Agent safety policy (ADR-0051) + ADR-0128.
 *
 * @param placeholderPolicy placeholder preservation (ADR-0051).
 * @param deniedSkillKeys   block list of {@code Skill.skillKey()} values.
 * @param deniedMemoryRefs  block list of memory references.
 * @param outputPolicy      content policy for outputs.
 */
public record SafetyPolicy(
        PlaceholderPreservationPolicy placeholderPolicy,
        Set<String> deniedSkillKeys,
        Set<String> deniedMemoryRefs,
        OutputContentPolicy outputPolicy) {

    public SafetyPolicy {
        Objects.requireNonNull(placeholderPolicy, "placeholderPolicy");
        Objects.requireNonNull(deniedSkillKeys, "deniedSkillKeys");
        Objects.requireNonNull(deniedMemoryRefs, "deniedMemoryRefs");
        Objects.requireNonNull(outputPolicy, "outputPolicy");
        deniedSkillKeys = Set.copyOf(deniedSkillKeys);
        deniedMemoryRefs = Set.copyOf(deniedMemoryRefs);
    }

    /** Permissive default: no denies, no PII redaction, preserve placeholders. */
    public static SafetyPolicy permissive() {
        return new SafetyPolicy(
                PlaceholderPreservationPolicy.PRESERVE,
                Set.of(),
                Set.of(),
                OutputContentPolicy.defaults());
    }
}
