package com.huawei.ascend.examples.workmate.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class WorkspacePathGuard {

    private WorkspacePathGuard() {
    }

    public static Path resolve(Path workspaceRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        Path workspace = workspaceRoot.toAbsolutePath().normalize();
        Path resolved = workspace.resolve(relativePath).normalize();
        if (!resolved.startsWith(workspace)) {
            throw new IllegalArgumentException("path escapes workspace: " + relativePath);
        }
        ensureRealPathWithinWorkspace(workspace, resolved, relativePath);
        return resolved;
    }

    private static void ensureRealPathWithinWorkspace(Path workspace, Path resolved, String relativePath) {
        try {
            Path realWorkspace = workspace.toRealPath();
            if (Files.exists(resolved)) {
                Path realResolved = resolved.toRealPath();
                if (!realResolved.startsWith(realWorkspace)) {
                    throw new IllegalArgumentException("path escapes workspace: " + relativePath);
                }
                return;
            }

            Path existingParent = resolved.getParent();
            while (existingParent != null && !Files.exists(existingParent)) {
                existingParent = existingParent.getParent();
            }
            if (existingParent == null) {
                throw new IllegalArgumentException("path parent does not exist: " + relativePath);
            }
            Path realParent = existingParent.toRealPath();
            if (!realParent.startsWith(realWorkspace)) {
                throw new IllegalArgumentException("path escapes workspace: " + relativePath);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("path cannot be resolved safely: " + relativePath, ex);
        }
    }

    static Map<String, Object> success(Object data) {
        return Map.of("success", true, "data", data);
    }

    static Map<String, Object> failure(String message) {
        return Map.of("success", false, "error", message);
    }

    public static String readText(Path file, int maxBytes) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("file not found: " + file.getFileName());
        }
        if (Files.size(file) > maxBytes) {
            throw new IOException("file too large (> " + maxBytes + " bytes)");
        }
        return Files.readString(file);
    }
}
