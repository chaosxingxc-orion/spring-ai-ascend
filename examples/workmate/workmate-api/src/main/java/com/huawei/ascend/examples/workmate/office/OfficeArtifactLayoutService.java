package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.session.WorkspaceException;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class OfficeArtifactLayoutService {

    private static final String REQUEST_PLACEHOLDER = """
            # Task request

            Describe the office task requirements here. The agent reads this file and materials under `inputs/`.
            """;

    public void bootstrapLayout(Path workspaceRoot, ExpertDefinition expert, UUID sessionId) {
        if (!OfficeArtifactContract.isOfficeCapable(expert)) {
            return;
        }
        String taskRoot = ensureSessionTaskRoot(workspaceRoot, expert, sessionId);
        try {
            Files.createDirectories(workspaceRoot.resolve(OfficeArtifactContract.inputsDir(taskRoot)));
            Files.createDirectories(workspaceRoot.resolve(OfficeArtifactContract.outputsDir(taskRoot)));
            Path request = workspaceRoot.resolve(taskRoot).resolve(OfficeArtifactContract.REQUEST_FILE);
            if (!Files.exists(request)) {
                Files.writeString(request, REQUEST_PLACEHOLDER);
            }
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to bootstrap office layout at " + taskRoot, ex);
        }
    }

    public String taskRootRelative(ExpertDefinition expert, UUID sessionId) {
        if (!OfficeArtifactContract.isOfficeCapable(expert)) {
            return null;
        }
        return OfficeArtifactContract.sessionTaskRoot(sessionId);
    }

    public String ensureSessionTaskRoot(Path workspaceRoot, ExpertDefinition expert, UUID sessionId) {
        String sessionRoot = OfficeArtifactContract.sessionTaskRoot(sessionId);
        Path sessionRootPath = workspaceRoot.resolve(sessionRoot);
        if (!Files.exists(sessionRootPath)) {
            migrateLegacyOfficeTree(workspaceRoot, expert, sessionId, sessionRootPath);
        }
        return sessionRoot;
    }

    private void migrateLegacyOfficeTree(
            Path workspaceRoot, ExpertDefinition expert, UUID sessionId, Path sessionRootPath) {
        String legacyRoot = OfficeArtifactContract.legacyTaskRoot(expert, sessionId);
        Path legacyPath = workspaceRoot.resolve(legacyRoot);
        if (!Files.isDirectory(legacyPath)) {
            return;
        }
        try {
            Files.createDirectories(sessionRootPath);
            copyTreeIfAbsent(legacyPath.resolve(OfficeArtifactContract.INPUTS_DIR),
                    sessionRootPath.resolve(OfficeArtifactContract.INPUTS_DIR));
            copyTreeIfAbsent(legacyPath.resolve(OfficeArtifactContract.OUTPUTS_DIR),
                    sessionRootPath.resolve(OfficeArtifactContract.OUTPUTS_DIR));
            Path legacyRequest = legacyPath.resolve(OfficeArtifactContract.REQUEST_FILE);
            Path targetRequest = sessionRootPath.resolve(OfficeArtifactContract.REQUEST_FILE);
            if (Files.isRegularFile(legacyRequest) && !Files.exists(targetRequest)) {
                Files.copy(legacyRequest, targetRequest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to migrate legacy office layout from " + legacyRoot, ex);
        }
    }

    private static void copyTreeIfAbsent(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        Files.createDirectories(target);
        try (Stream<Path> walk = Files.walk(source, FileVisitOption.FOLLOW_LINKS)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path destination = target.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else if (!Files.exists(destination)) {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination);
                    }
                } catch (IOException ex) {
                    throw new WorkspaceException("Failed to copy office artifact " + path, ex);
                }
            });
        }
    }
}
