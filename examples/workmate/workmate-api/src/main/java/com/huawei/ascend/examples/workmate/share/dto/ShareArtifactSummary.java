package com.huawei.ascend.examples.workmate.share.dto;

import com.huawei.ascend.examples.workmate.artifact.dto.ArtifactResponse;
import java.time.Instant;

public record ShareArtifactSummary(String path, String name, String mime, long size, Instant updatedAt) {

    public static ShareArtifactSummary from(ArtifactResponse artifact) {
        return new ShareArtifactSummary(
                artifact.path(), artifact.name(), artifact.mime(), artifact.size(), artifact.updatedAt());
    }
}
