package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.OfficeAssetSource;
import com.huawei.ascend.examples.workmate.office.SkillRegistryEntry;
import com.huawei.ascend.examples.workmate.office.SkillYamlWriter;

public record StudioSkillSourceResponse(
        SkillSummaryResponse summary,
        String skillFile,
        String skillContent,
        String skillYaml,
        OfficeAssetSource source,
        String sourceDir) {

    public static StudioSkillSourceResponse from(SkillRegistryEntry entry, boolean installed) {
        var skill = entry.definition();
        return new StudioSkillSourceResponse(
                SkillSummaryResponse.from(skill, installed),
                entry.skillFile(),
                skill.skillBody(),
                SkillYamlWriter.render(skill, entry.skillFile()),
                entry.source(),
                entry.sourceDir().toString());
    }
}
