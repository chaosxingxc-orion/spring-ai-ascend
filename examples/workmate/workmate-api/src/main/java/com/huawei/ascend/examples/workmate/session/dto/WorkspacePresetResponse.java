package com.huawei.ascend.examples.workmate.session.dto;

import com.huawei.ascend.examples.workmate.config.WorkmateWorkspaceProperties.WorkspacePreset;

public record WorkspacePresetResponse(String id, String name, String path, String description) {

    public static WorkspacePresetResponse from(WorkspacePreset preset) {
        return new WorkspacePresetResponse(preset.id(), preset.name(), preset.path(), preset.description());
    }
}
