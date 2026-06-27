package com.huawei.ascend.examples.workmate.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.workspace")
public record WorkmateWorkspaceProperties(String basePath, List<WorkspacePreset> presets) {

    public WorkmateWorkspaceProperties {
        if (basePath == null || basePath.isBlank()) {
            basePath = "./workspaces";
        }
        if (presets == null) {
            presets = List.of();
        }
    }

    public record WorkspacePreset(String id, String name, String path, String description) {

        public WorkspacePreset {
            if (path == null) {
                path = "";
            }
            if (description == null) {
                description = "";
            }
        }
    }
}
