package com.huawei.ascend.examples.workmate.artifact;

import java.time.Instant;

public record ArtifactEntry(String path, String name, String mime, long size, Instant updatedAt) {
}
