package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.OfficeAssetSource;
import com.huawei.ascend.examples.workmate.office.PlaybookRegistryEntry;
import java.util.List;

public record StudioPlaybookListItemResponse(
        String id,
        String title,
        String description,
        List<String> placements,
        OfficeAssetSource source,
        String sourcePath) {

    public static StudioPlaybookListItemResponse from(PlaybookRegistryEntry entry) {
        var playbook = entry.definition();
        return new StudioPlaybookListItemResponse(
                playbook.id(),
                playbook.title(),
                playbook.description(),
                playbook.placements(),
                entry.source(),
                entry.sourcePath().toString());
    }
}
