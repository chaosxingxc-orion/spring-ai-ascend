package com.huawei.ascend.examples.workmate.session;

import com.huawei.ascend.examples.workmate.config.WorkmateWorkspaceProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateWorkspaceProperties.WorkspacePreset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {

    private final Path basePath;
    private final List<WorkspacePreset> presets;

    public WorkspaceService(WorkmateWorkspaceProperties properties) {
        this.basePath = Paths.get(properties.basePath()).toAbsolutePath().normalize();
        this.presets = properties.presets();
        ensurePresetDirectories();
    }

    public Path basePath() {
        return basePath;
    }

    public List<WorkspacePreset> presets() {
        return presets;
    }

    public String workspaceKey(String workspaceRoot) {
        Path normalized = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        return basePath.relativize(normalized).toString().replace('\\', '/');
    }

    private void ensurePresetDirectories() {
        for (WorkspacePreset preset : presets) {
            if (preset.path() == null || preset.path().isBlank()) {
                continue;
            }
            Path presetPath = basePath.resolve(preset.path()).normalize();
            validateWithinBase(presetPath);
            try {
                Files.createDirectories(presetPath);
            } catch (IOException ex) {
                throw new WorkspaceException("Failed to create preset workspace " + presetPath, ex);
            }
        }
    }

    /**
     * Creates the workspace directory on disk. Idempotent if the directory already exists.
     */
    public Path createWorkspace(Path workspaceRoot) {
        Path workspace = workspaceRoot.toAbsolutePath().normalize();
        validateWithinBase(workspace);
        try {
            Files.createDirectories(workspace);
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to create workspace at " + workspace, ex);
        }
        return workspace;
    }

    public Path resolveSessionRoot(UUID sessionId) {
        return basePath.resolve(sessionId.toString()).normalize();
    }

    public void validateWithinBase(Path workspaceRoot) {
        Path normalized = workspaceRoot.toAbsolutePath().normalize();
        if (!normalized.startsWith(basePath)) {
            throw new WorkspaceException("Workspace path must stay under " + basePath);
        }
    }
}
