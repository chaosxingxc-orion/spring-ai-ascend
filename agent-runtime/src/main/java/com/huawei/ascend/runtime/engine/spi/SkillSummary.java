package com.huawei.ascend.runtime.engine.spi;

import java.util.List;
import java.util.Map;

/**
 * Compact SkillHub entry used for discovery before full skill instructions are
 * loaded.
 */
public record SkillSummary(
        String skillId,
        String name,
        String description,
        List<String> tags,
        Map<String, Object> metadata) {

    public SkillSummary {
        skillId = normalizeRequired(skillId, "skillId");
        name = normalizeDefault(name, skillId);
        description = description == null ? "" : description;
        tags = tags == null ? List.of() : List.copyOf(tags);
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
