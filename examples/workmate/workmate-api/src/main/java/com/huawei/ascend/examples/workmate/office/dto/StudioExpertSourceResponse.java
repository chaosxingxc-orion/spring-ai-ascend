package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.ExpertRegistryEntry;
import com.huawei.ascend.examples.workmate.office.ExpertYamlWriter;
import com.huawei.ascend.examples.workmate.office.OfficeAssetSource;

public record StudioExpertSourceResponse(
        ExpertSummaryResponse summary,
        String promptFile,
        String promptContent,
        String expertYaml,
        OfficeAssetSource source,
        String sourceDir) {

    public static StudioExpertSourceResponse from(ExpertRegistryEntry entry) {
        var expert = entry.definition();
        return new StudioExpertSourceResponse(
                ExpertSummaryResponse.from(expert),
                entry.promptFile(),
                expert.systemPrompt(),
                ExpertYamlWriter.render(expert, entry.promptFile()),
                entry.source(),
                entry.sourceDir().toString());
    }
}
