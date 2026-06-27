package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.OfficeAssetSource;
import com.huawei.ascend.examples.workmate.office.SkillRegistryEntry;

public record StudioSkillListItemResponse(
        SkillSummaryResponse summary,
        OfficeAssetSource source,
        String sourceDir,
        String skillFile) {

    public static StudioSkillListItemResponse from(SkillRegistryEntry entry, boolean installed) {
        return new StudioSkillListItemResponse(
                SkillSummaryResponse.from(entry.definition(), installed),
                entry.source(),
                entry.sourceDir().toString(),
                entry.skillFile());
    }
}
