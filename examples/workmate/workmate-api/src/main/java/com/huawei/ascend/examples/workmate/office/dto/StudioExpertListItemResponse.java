package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.ExpertRegistryEntry;
import com.huawei.ascend.examples.workmate.office.OfficeAssetSource;

public record StudioExpertListItemResponse(
        ExpertSummaryResponse summary,
        OfficeAssetSource source,
        String sourceDir,
        String promptFile) {

    public static StudioExpertListItemResponse from(ExpertRegistryEntry entry) {
        return new StudioExpertListItemResponse(
                ExpertSummaryResponse.from(entry.definition()),
                entry.source(),
                entry.sourceDir().toString(),
                entry.promptFile());
    }
}
