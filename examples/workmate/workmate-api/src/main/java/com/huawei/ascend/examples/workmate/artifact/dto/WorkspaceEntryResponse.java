package com.huawei.ascend.examples.workmate.artifact.dto;

import java.time.Instant;

public record WorkspaceEntryResponse(
        String name,
        String path,
        String type,
        Long size,
        String mime,
        Instant updatedAt) {

    public static WorkspaceEntryResponse directory(String name, String path, Instant updatedAt) {
        return new WorkspaceEntryResponse(name, path, "dir", null, null, updatedAt);
    }

    public static WorkspaceEntryResponse file(
            String name, String path, long size, String mime, Instant updatedAt) {
        return new WorkspaceEntryResponse(name, path, "file", size, mime, updatedAt);
    }
}
