package com.huawei.ascend.examples.workmate.myfiles;

import com.huawei.ascend.examples.workmate.artifact.ArtifactEntry;
import com.huawei.ascend.examples.workmate.artifact.ArtifactMimeTypes;
import com.huawei.ascend.examples.workmate.artifact.ArtifactNotFoundException;
import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.myfiles.dto.MyFileResponse;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.session.WorkmateSessionRepository;
import com.huawei.ascend.examples.workmate.session.WorkspaceException;
import com.huawei.ascend.examples.workmate.tools.WorkspacePathGuard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MyFilesService {

    private static final int MAX_DOWNLOAD_BYTES = 50 * 1024 * 1024;

    private final WorkmateSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final ArtifactService artifactService;
    private final FavoriteStore favoriteStore;
    private final AuditLedgerService auditLedgerService;

    public MyFilesService(
            WorkmateSessionRepository sessionRepository,
            SessionService sessionService,
            ArtifactService artifactService,
            FavoriteStore favoriteStore,
            AuditLedgerService auditLedgerService) {
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.artifactService = artifactService;
        this.favoriteStore = favoriteStore;
        this.auditLedgerService = auditLedgerService;
    }

    public List<MyFileResponse> list(String query, String sort, String order, boolean favoritesOnly) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<MyFileEntry> entries = new ArrayList<>();
        for (WorkmateSession session : sessionRepository.findAllByOrderByUpdatedAtDesc()) {
            Path workspace = Path.of(session.getWorkspaceRoot()).toAbsolutePath().normalize();
            if (!Files.isDirectory(workspace)) {
                continue;
            }
            for (ArtifactEntry artifact : artifactService.scanWorkspace(workspace)) {
                boolean favorite = favoriteStore.isFavorite(session.getId(), artifact.path());
                MyFileEntry entry = new MyFileEntry(
                        session.getId(),
                        session.getTitle(),
                        artifact.path(),
                        artifact.name(),
                        artifact.mime(),
                        artifact.size(),
                        artifact.updatedAt(),
                        favorite);
                if (favoritesOnly && !favorite) {
                    continue;
                }
                if (!normalizedQuery.isEmpty()) {
                    String haystack = (entry.sessionTitle() + " " + entry.path() + " " + entry.name())
                            .toLowerCase(Locale.ROOT);
                    if (!haystack.contains(normalizedQuery)) {
                        continue;
                    }
                }
                entries.add(entry);
            }
        }
        Comparator<MyFileEntry> comparator = comparatorFor(sort, order);
        entries.sort(comparator);
        return entries.stream().map(MyFileResponse::from).toList();
    }

    public MyFileResponse rename(UUID sessionId, String relativePath, String newName) {
        Path workspace = workspaceRoot(sessionId);
        String sourcePath = normalizePath(relativePath);
        Path source = WorkspacePathGuard.resolve(workspace, sourcePath);
        if (!Files.isRegularFile(source)) {
            throw new ArtifactNotFoundException("File not found: " + sourcePath);
        }
        String trimmedName = newName.trim();
        if (trimmedName.isBlank() || trimmedName.contains("/") || trimmedName.contains("\\")) {
            throw new IllegalArgumentException("newName must be a single file name");
        }
        Path parent = source.getParent() != null ? source.getParent() : workspace;
        Path dest = parent.resolve(trimmedName).normalize();
        if (!dest.startsWith(workspace)) {
            throw new IllegalArgumentException("Renamed path escapes workspace");
        }
        String destPath = workspace.relativize(dest).toString().replace('\\', '/');
        try {
            Files.move(source, dest);
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to rename " + sourcePath, ex);
        }
        favoriteStore.relocateFavorite(sessionId, sourcePath, destPath);
        recordAudit(sessionId, "myfiles.renamed", Map.of(
                "path", sourcePath,
                "destPath", destPath,
                "newName", trimmedName));
        return toResponse(sessionId, destPath, dest);
    }

    public MyFileResponse move(UUID sessionId, String relativePath, String destRelativePath) {
        Path workspace = workspaceRoot(sessionId);
        String sourcePath = normalizePath(relativePath);
        String destPath = normalizePath(destRelativePath);
        Path source = WorkspacePathGuard.resolve(workspace, sourcePath);
        Path dest = WorkspacePathGuard.resolve(workspace, destPath);
        if (!Files.isRegularFile(source)) {
            throw new ArtifactNotFoundException("File not found: " + sourcePath);
        }
        if (dest.equals(source)) {
            return toResponse(sessionId, sourcePath, source);
        }
        try {
            Files.createDirectories(dest.getParent() != null ? dest.getParent() : workspace);
            Files.move(source, dest);
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to move " + sourcePath + " to " + destPath, ex);
        }
        favoriteStore.relocateFavorite(sessionId, sourcePath, destPath);
        recordAudit(sessionId, "myfiles.moved", Map.of("path", sourcePath, "destPath", destPath));
        return toResponse(sessionId, destPath, dest);
    }

    public void delete(UUID sessionId, String relativePath) {
        Path workspace = workspaceRoot(sessionId);
        String path = normalizePath(relativePath);
        Path file = WorkspacePathGuard.resolve(workspace, path);
        if (!Files.isRegularFile(file)) {
            throw new ArtifactNotFoundException("File not found: " + path);
        }
        try {
            Files.delete(file);
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to delete " + path, ex);
        }
        favoriteStore.removeFavorite(sessionId, path);
        recordAudit(sessionId, "myfiles.deleted", Map.of("path", path));
    }

    public MyFileResponse setFavorite(UUID sessionId, String relativePath, boolean favorite) {
        Path workspace = workspaceRoot(sessionId);
        String path = normalizePath(relativePath);
        Path file = WorkspacePathGuard.resolve(workspace, path);
        if (!Files.isRegularFile(file)) {
            throw new ArtifactNotFoundException("File not found: " + path);
        }
        favoriteStore.setFavorite(sessionId, path, favorite);
        recordAudit(sessionId, "myfiles.favorite", Map.of("path", path, "favorite", favorite));
        return toResponse(sessionId, path, file);
    }

    public ResponseEntity<Resource> download(UUID sessionId, String relativePath) {
        Path workspace = workspaceRoot(sessionId);
        String path = normalizePath(relativePath);
        Path file = WorkspacePathGuard.resolve(workspace, path);
        if (!Files.isRegularFile(file)) {
            throw new ArtifactNotFoundException("File not found: " + path);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_DOWNLOAD_BYTES) {
                throw new IllegalArgumentException("File too large to download: " + path);
            }
            byte[] bytes = Files.readAllBytes(file);
            String mime = ArtifactMimeTypes.guess(file.getFileName().toString());
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(file.getFileName().toString())
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(MediaType.parseMediaType(mime))
                    .contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to download " + path, ex);
        }
    }

    private MyFileResponse toResponse(UUID sessionId, String path, Path file) {
        WorkmateSession session = sessionService.requireSession(sessionId);
        try {
            long size = Files.size(file);
            Instant updatedAt = Files.getLastModifiedTime(file).toInstant();
            String name = file.getFileName().toString();
            boolean favorite = favoriteStore.isFavorite(sessionId, path);
            return MyFileResponse.from(new MyFileEntry(
                    sessionId,
                    session.getTitle(),
                    path,
                    name,
                    ArtifactMimeTypes.guess(name),
                    size,
                    updatedAt,
                    favorite));
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to stat file " + path, ex);
        }
    }

    private Path workspaceRoot(UUID sessionId) {
        WorkmateSession session = sessionService.requireSession(sessionId);
        Path workspace = Path.of(session.getWorkspaceRoot()).toAbsolutePath().normalize();
        if (!Files.isDirectory(workspace)) {
            throw new ArtifactNotFoundException("Workspace not found for session " + sessionId);
        }
        return workspace;
    }

    private void recordAudit(UUID sessionId, String eventName, Map<String, Object> payload) {
        Map<String, Object> audit = new LinkedHashMap<>(payload);
        auditLedgerService.record(RunPersistenceContext.forAudit(sessionId, "myfiles"), eventName, audit);
    }

    private static Comparator<MyFileEntry> comparatorFor(String sort, String order) {
        boolean ascending = "asc".equalsIgnoreCase(order);
        Comparator<MyFileEntry> base = "name".equalsIgnoreCase(sort)
                ? Comparator.comparing(MyFileEntry::name, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparing(MyFileEntry::updatedAt);
        if (!ascending) {
            base = base.reversed();
        }
        return base.thenComparing(MyFileEntry::path, String.CASE_INSENSITIVE_ORDER);
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("path must not be blank");
        }
        return path.replace('\\', '/').trim();
    }
}
