package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.SkillDefinition;
import java.util.List;

public record SkillSummaryResponse(
        String id,
        String name,
        String description,
        String category,
        List<String> tags,
        String source,
        boolean installed,
        boolean policyLocked) {

    public static SkillSummaryResponse from(SkillDefinition skill, boolean installed) {
        return new SkillSummaryResponse(
                skill.id(),
                skill.name(),
                skill.description(),
                skill.category(),
                skill.tags(),
                skill.source(),
                installed,
                skill.defaultInstalled());
    }
}
