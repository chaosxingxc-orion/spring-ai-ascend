package com.huawei.ascend.examples.workmate.artifact.dto;

import com.huawei.ascend.examples.workmate.artifact.ArtifactEntry;
import com.huawei.ascend.examples.workmate.office.OfficeArtifactContract;
import java.time.Instant;

public record ArtifactResponse(
        String path,
        String name,
        String mime,
        long size,
        Instant updatedAt,
        String officeCapability,
        String officeZone) {

    public static ArtifactResponse from(ArtifactEntry entry) {
        OfficeArtifactContract.OfficeArtifactMeta meta =
                OfficeArtifactContract.parseRelativePath(entry.path()).orElse(null);
        return new ArtifactResponse(
                entry.path(),
                entry.name(),
                entry.mime(),
                entry.size(),
                entry.updatedAt(),
                meta != null ? meta.capability() : null,
                meta != null ? meta.zone() : null);
    }
}
