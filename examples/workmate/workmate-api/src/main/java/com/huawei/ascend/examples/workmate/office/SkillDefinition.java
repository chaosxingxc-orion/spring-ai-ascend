package com.huawei.ascend.examples.workmate.office;

import java.util.List;

public record SkillDefinition(
        String id,
        String name,
        String description,
        String category,
        List<String> tags,
        String source,
        boolean defaultInstalled,
        String skillBody) {

    public SkillDefinition {
        if (tags == null) {
            tags = List.of();
        }
        if (category == null || category.isBlank()) {
            category = "general";
        }
        if (source == null || source.isBlank()) {
            source = "builtin";
        } else {
            source = OfficeMarketText.normalizeSource(source);
        }
    }
}
