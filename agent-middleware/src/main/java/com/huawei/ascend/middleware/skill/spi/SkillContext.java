package com.huawei.ascend.middleware.skill.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Passed to {@link Skill#init(SkillContext)} on registration.
 *
 * <p>Authority: ADR-0127. Carries tenant scope, trace context, and
 * a config map the skill may use to set itself up.
 *
 * @param tenantId  owning tenant (Rule R-C.c).
 * @param traceId   correlation id for the init operation (nullable).
 * @param config    free-form configuration map sourced from
 *                  {@code @ConfigurationProperties}.
 */
public record SkillContext(String tenantId, String traceId, Map<String, Object> config) {
    public SkillContext {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(config, "config");
        config = Map.copyOf(config);
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank (Rule R-C.c)");
        }
    }
}
