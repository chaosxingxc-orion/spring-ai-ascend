package com.huawei.ascend.runtime.engine.spi;

import java.util.List;
import java.util.Map;

/** Full SkillHub definition loaded after a {@link SkillSummary} is selected. */
public record SkillDefinition(
        String skillId,
        String name,
        String description,
        String instructions,
        List<String> referenceUris,
        List<SkillToolDependency> toolDependencies,
        Map<String, Object> metadata) {

    public SkillDefinition {
        skillId = normalizeRequired(skillId, "skillId");
        name = normalizeDefault(name, skillId);
        description = description == null ? "" : description;
        instructions = instructions == null ? "" : instructions;
        referenceUris = referenceUris == null ? List.of() : List.copyOf(referenceUris);
        toolDependencies = toolDependencies == null ? List.of() : List.copyOf(toolDependencies);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String normalizeDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
