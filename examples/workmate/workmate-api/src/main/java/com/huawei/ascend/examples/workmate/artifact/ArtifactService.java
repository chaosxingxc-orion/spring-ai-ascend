package com.huawei.ascend.examples.workmate.artifact;

import com.huawei.ascend.examples.workmate.artifact.dto.ArtifactResponse;
import com.huawei.ascend.examples.workmate.artifact.dto.FileContentResponse;
import com.huawei.ascend.examples.workmate.artifact.dto.UserAttachmentResponse;
import com.huawei.ascend.examples.workmate.artifact.dto.WorkspaceEntryResponse;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.session.WorkspaceException;
import com.huawei.ascend.examples.workmate.tools.WorkspacePathGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ArtifactService {

    private static final int MAX_READ_BYTES = 512 * 1024;
    private static final int MAX_PREVIEW_BYTES = 10 * 1024 * 1024;
    private static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    private static final String UPLOADS_DIR = "uploads";
    private static final Set<String> UPLOAD_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg");
    private static final Set<String> PREVIEW_EXTENSIONS = Set.of(
            ".html", ".htm", ".css", ".js", ".mjs", ".json", ".map",
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".woff", ".woff2", ".ico",
            ".pdf", ".mp4", ".webm", ".mov", ".m4v");

    private final SessionService sessionService;

    public ArtifactService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public List<ArtifactResponse> listArtifacts(UUID sessionId) {
        Path workspace = workspaceRoot(sessionId);
        return scanWorkspace(workspace).stream().map(ArtifactResponse::from).toList();
    }

    /** W35 — reusable workspace scan for myFiles aggregation. */
    public List<ArtifactEntry> scanWorkspace(Path workspace) {
        return scan(workspace);
    }

    public List<WorkspaceEntryResponse> listDirectory(UUID sessionId, String relativePath) {
        Path workspace = workspaceRoot(sessionId);
        Path dir = StringUtils.hasText(relativePath)
                ? WorkspacePathGuard.resolve(workspace, relativePath)
                : workspace;
        if (!Files.isDirectory(dir)) {
            throw new ArtifactNotFoundException("Directory not found: " + relativePath);
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                    .filter(ArtifactService::isVisiblePath)
                    .sorted(WorkspaceEntryResponseComparator.INSTANCE)
                    .map(path -> toWorkspaceEntry(workspace, path))
                    .toList();
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to list directory: " + relativePath, ex);
        }
    }

    public ResponseEntity<Resource> servePreviewFile(UUID sessionId, String relativePath) {
        Path workspace = workspaceRoot(sessionId);
        String normalized = relativePath.replace('\\', '/').trim();
        if (normalized.isBlank()) {
            throw new ArtifactNotFoundException("Preview path required");
        }
        Path file = WorkspacePathGuard.resolve(workspace, normalized);
        if (!Files.isRegularFile(file)) {
            throw new ArtifactNotFoundException("File not found: " + normalized);
        }
        if (!isPreviewable(file)) {
            throw new PreviewNotAllowedException("File type not previewable: " + normalized);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_PREVIEW_BYTES) {
                throw new PreviewNotAllowedException("Preview file too large: " + normalized);
            }
            byte[] bytes = Files.readAllBytes(file);
            String mime = ArtifactMimeTypes.guess(file.getFileName().toString());
            MediaType mediaType = MediaType.parseMediaType(mime);
            // Agent-authored HTML/JS/SVG is untrusted. Serve it sandboxed so it cannot run scripts in
            // the API origin, read same-origin cookies, or frame-bust. Pair with serving previews from a
            // dedicated origin in production.
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header("Content-Security-Policy",
                            "sandbox; default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; font-src 'self' data:")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("X-Frame-Options", "SAMEORIGIN")
                    .contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to read preview file: " + normalized, ex);
        }
    }

    public FileContentResponse readFile(UUID sessionId, String relativePath) {
        Path workspace = workspaceRoot(sessionId);
        Path file = WorkspacePathGuard.resolve(workspace, relativePath);
        if (!Files.isRegularFile(file)) {
            throw new ArtifactNotFoundException("File not found: " + relativePath);
        }
        try {
            long size = Files.size(file);
            boolean truncated = size > MAX_READ_BYTES;
            byte[] bytes;
            try (var input = Files.newInputStream(file)) {
                bytes = input.readNBytes(MAX_READ_BYTES + 1);
            }
            truncated = bytes.length > MAX_READ_BYTES || truncated;
            String content = truncated
                    ? new String(bytes, 0, Math.min(bytes.length, MAX_READ_BYTES), StandardCharsets.UTF_8)
                    : new String(bytes, StandardCharsets.UTF_8);
            String pathKey = workspace.relativize(file).toString().replace('\\', '/');
            return new FileContentResponse(
                    pathKey,
                    ArtifactMimeTypes.guess(file.getFileName().toString()),
                    content,
                    size,
                    truncated);
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to read file: " + relativePath, ex);
        }
    }

    public Map<String, Instant> snapshotRelativePaths(UUID sessionId) {
        Path workspace = workspaceRoot(sessionId);
        Map<String, Instant> snapshot = new HashMap<>();
        for (ArtifactEntry entry : scan(workspace)) {
            snapshot.put(entry.path(), entry.updatedAt());
        }
        return snapshot;
    }

    /** Filter workspace files by path/name for @-mention menu (W30). */
    public List<ArtifactResponse> searchFiles(UUID sessionId, String query, int limit) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        int capped = Math.min(Math.max(limit, 1), 50);
        return listArtifacts(sessionId).stream()
                .filter(item -> normalized.isEmpty()
                        || item.path().toLowerCase().contains(normalized)
                        || item.name().toLowerCase().contains(normalized))
                .limit(capped)
                .toList();
    }

    public List<ArtifactResponse> diffArtifacts(Map<String, Instant> before, UUID sessionId) {
        Map<String, Instant> after = snapshotRelativePaths(sessionId);
        Set<String> changed = new HashSet<>();
        for (Map.Entry<String, Instant> entry : after.entrySet()) {
            Instant previous = before.get(entry.getKey());
            if (previous == null || previous.isBefore(entry.getValue())) {
                changed.add(entry.getKey());
            }
        }
        if (changed.isEmpty()) {
            return List.of();
        }
        return listArtifacts(sessionId).stream()
                .filter(item -> changed.contains(item.path()))
                .toList();
    }

    /** R1 — store user image attachment under workspace uploads/. */
    public UserAttachmentResponse uploadUserAttachment(UUID sessionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Attachment file is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Attachment exceeds 10 MB limit");
        }
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            throw new IllegalArgumentException("Attachment filename is required");
        }
        String safeName = sanitizeUploadName(originalName);
        String extension = fileExtension(safeName);
        if (!UPLOAD_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported attachment type: " + extension);
        }

        Path workspace = workspaceRoot(sessionId);
        Path uploadsDir = WorkspacePathGuard.resolve(workspace, UPLOADS_DIR);
        String storedName = UUID.randomUUID() + "-" + safeName;
        Path target = uploadsDir.resolve(storedName).normalize();
        if (!target.startsWith(uploadsDir)) {
            throw new IllegalArgumentException("Invalid attachment path");
        }
        try {
            Files.createDirectories(uploadsDir);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to store attachment", ex);
        }

        String relative = workspace.relativize(target).toString().replace('\\', '/');
        String mime = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : ArtifactMimeTypes.guess(safeName);
        return new UserAttachmentResponse(relative, safeName, mime);
    }

    private static String sanitizeUploadName(String name) {
        String base = Path.of(name).getFileName().toString().trim();
        if (base.isEmpty()) {
            throw new IllegalArgumentException("Attachment filename is required");
        }
        String cleaned = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Attachment filename is invalid");
        }
        return cleaned;
    }

    private static String fileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private Path workspaceRoot(UUID sessionId) {
        WorkmateSession session = sessionService.requireSession(sessionId);
        Path workspace = Path.of(session.getWorkspaceRoot()).toAbsolutePath().normalize();
        if (!Files.isDirectory(workspace)) {
            throw new ArtifactNotFoundException("Workspace not found for session " + sessionId);
        }
        return workspace;
    }

    private static List<ArtifactEntry> scan(Path workspace) {
        if (!Files.isDirectory(workspace)) {
            return List.of();
        }
        try (var paths = Files.walk(workspace)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(ArtifactService::isVisibleArtifact)
                    .map(path -> toEntry(workspace, path))
                    .sorted(Comparator.comparing(ArtifactEntry::updatedAt).reversed())
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to scan workspace " + workspace, ex);
        }
    }

    private static boolean isPreviewable(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return PREVIEW_EXTENSIONS.contains(name.substring(dot));
    }

    private static boolean isVisiblePath(Path path) {
        Path name = path.getFileName();
        return name != null && !name.toString().startsWith(".");
    }

    private static boolean isVisibleArtifact(Path path) {
        for (Path part : path) {
            String name = part.toString();
            if (name.startsWith(".")) {
                return false;
            }
        }
        return true;
    }

    private static WorkspaceEntryResponse toWorkspaceEntry(Path workspace, Path path) {
        String relative = workspace.relativize(path).toString().replace('\\', '/');
        String name = path.getFileName().toString();
        try {
            Instant updatedAt = Files.getLastModifiedTime(path).toInstant();
            if (Files.isDirectory(path)) {
                return WorkspaceEntryResponse.directory(name, relative, updatedAt);
            }
            long size = Files.size(path);
            return WorkspaceEntryResponse.file(
                    name, relative, size, ArtifactMimeTypes.guess(name), updatedAt);
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to stat entry " + relative, ex);
        }
    }

    private enum WorkspaceEntryResponseComparator implements Comparator<Path> {
        INSTANCE;

        @Override
        public int compare(Path left, Path right) {
            boolean leftDir = Files.isDirectory(left);
            boolean rightDir = Files.isDirectory(right);
            if (leftDir != rightDir) {
                return leftDir ? -1 : 1;
            }
            return left.getFileName().toString().compareToIgnoreCase(right.getFileName().toString());
        }
    }

    private static ArtifactEntry toEntry(Path workspace, Path file) {
        String relative = workspace.relativize(file).toString().replace('\\', '/');
        String name = file.getFileName().toString();
        try {
            long size = Files.size(file);
            FileTime mtime = Files.getLastModifiedTime(file);
            Instant updatedAt = mtime.toInstant();
            return new ArtifactEntry(relative, name, ArtifactMimeTypes.guess(name), size, updatedAt);
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to stat file " + relative, ex);
        }
    }
}
