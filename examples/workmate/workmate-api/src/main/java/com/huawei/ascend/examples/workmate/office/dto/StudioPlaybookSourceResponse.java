package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.OfficeAssetSource;
import com.huawei.ascend.examples.workmate.office.PlaybookRegistryEntry;
import com.huawei.ascend.examples.workmate.office.PlaybookYamlWriter;
import java.util.List;

public record StudioPlaybookSourceResponse(
        String id,
        String title,
        String description,
        String accent,
        String expertId,
        String initPrompt,
        List<String> placements,
        String playbookYaml,
        OfficeAssetSource source,
        String sourcePath) {

    public static StudioPlaybookSourceResponse from(PlaybookRegistryEntry entry) {
        var playbook = entry.definition();
        return new StudioPlaybookSourceResponse(
                playbook.id(),
                playbook.title(),
                playbook.description(),
                playbook.accent(),
                playbook.expertId(),
                playbook.initPrompt(),
                playbook.placements(),
                PlaybookYamlWriter.render(playbook),
                entry.source(),
                entry.sourcePath().toString());
    }
}
