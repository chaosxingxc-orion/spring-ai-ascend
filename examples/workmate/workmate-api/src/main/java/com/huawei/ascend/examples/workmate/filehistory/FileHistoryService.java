package com.huawei.ascend.examples.workmate.filehistory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.artifact.ArtifactMimeTypes;
import com.huawei.ascend.examples.workmate.filehistory.dto.FileChangeResponse;
import com.huawei.ascend.examples.workmate.filehistory.dto.FileDiffResponse;
import com.huawei.ascend.examples.workmate.filehistory.dto.FileVersionResponse;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.session.WorkspaceException;
import com.huawei.ascend.examples.workmate.tools.WorkspacePathGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class FileHistoryService {

    static final String HISTORY_DIR = ".file-history";
    private static final String INDEX_FILE = "index.jsonl";
    private static final String BLOBS_DIR = "blobs";
    private static final int MAX_DIFF_BYTES = 512 * 1024;

    private final SessionService sessionService;
    private final AuditLedgerService auditLedgerService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<UUID, Object> sessionLocks = new ConcurrentHashMap<>();

    public FileHistoryService(
            SessionService sessionService, AuditLedgerService auditLedgerService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.auditLedgerService = auditLedgerService;
        this.objectMapper = objectMapper;
    }

    /** Snapshot before agent write/edit. Call from workspace write tool only. */
    public void recordBeforeWrite(UUID sessionId, Path workspaceRoot, String relativePath, String runId) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        synchronized (lock(sessionId)) {
            try {
                Path file = WorkspacePathGuard.resolve(workspaceRoot, relativePath);
                Path historyRoot = historyRoot(workspaceRoot);
                Files.createDirectories(historyRoot.resolve(BLOBS_DIR));
                String normalizedPath = normalizePath(relativePath);
                boolean exists = Files.isRegularFile(file);
                FileVersionOp op = exists ? FileVersionOp.MODIFIED : FileVersionOp.CREATED;
                String versionId = UUID.randomUUID().toString();
                long bytes = 0;
                String sha256 = "";
                if (exists) {
                    byte[] content = Files.readAllBytes(file);
                    bytes = content.length;
                    sha256 = sha256Hex(content);
                    Files.write(historyRoot.resolve(BLOBS_DIR).resolve(versionId), content);
                }
                long seq = nextSeq(historyRoot);
                FileVersionEntry entry = new FileVersionEntry(
                        seq,
                        normalizedPath,
                        op,
                        versionId,
                        Instant.now(),
                        runId != null ? runId : "",
                        bytes,
                        sha256);
                appendIndex(historyRoot, entry);
            } catch (IOException ex) {
                throw new WorkspaceException("Failed to record file history for " + relativePath, ex);
            }
        }
    }

    public List<FileVersionResponse> listVersions(UUID sessionId, String relativePath) {
        Path workspace = workspaceRoot(sessionId);
        String normalizedPath = normalizePath(relativePath);
        Path historyRoot = historyRoot(workspace);
        if (!Files.isRegularFile(historyRoot.resolve(INDEX_FILE))) {
            return List.of();
        }
        synchronized (lock(sessionId)) {
            return readAllEntries(historyRoot).stream()
                    .filter(entry -> entry.path().equals(normalizedPath))
                    .map(FileVersionResponse::from)
                    .toList();
        }
    }

    /** Latest change per path from file-history (session workspace changes). */
    public List<FileChangeResponse> listSessionChanges(UUID sessionId) {
        Path workspace = workspaceRoot(sessionId);
        Path historyRoot = historyRoot(workspace);
        if (!Files.isRegularFile(historyRoot.resolve(INDEX_FILE))) {
            return List.of();
        }
        synchronized (lock(sessionId)) {
            Map<String, FileVersionEntry> latestByPath = new LinkedHashMap<>();
            for (FileVersionEntry entry : readAllEntries(historyRoot)) {
                latestByPath.put(entry.path(), entry);
            }
            return latestByPath.values().stream()
                    .sorted((left, right) -> right.ts().compareTo(left.ts()))
                    .map(FileChangeResponse::from)
                    .toList();
        }
    }

    public FileDiffResponse readDiff(UUID sessionId, String relativePath) {
        Path workspace = workspaceRoot(sessionId);
        String normalizedPath = normalizePath(relativePath);
        Path file = WorkspacePathGuard.resolve(workspace, normalizedPath);
        synchronized (lock(sessionId)) {
            Path historyRoot = historyRoot(workspace);
            List<FileVersionEntry> entries = readAllEntries(historyRoot).stream()
                    .filter(entry -> entry.path().equals(normalizedPath))
                    .toList();
            String original = "";
            if (!entries.isEmpty()) {
                FileVersionEntry latest = entries.getLast();
                if (latest.op() == FileVersionOp.MODIFIED) {
                    Path blob = historyRoot.resolve(BLOBS_DIR).resolve(latest.versionId());
                    if (Files.isRegularFile(blob)) {
                        try {
                            original = Files.readString(blob, StandardCharsets.UTF_8);
                        } catch (IOException ex) {
                            throw new WorkspaceException("Failed to read diff original for " + normalizedPath, ex);
                        }
                    }
                }
            }
            String modified = "";
            boolean truncated = false;
            if (Files.isRegularFile(file)) {
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    truncated = bytes.length > MAX_DIFF_BYTES;
                    int limit = truncated ? MAX_DIFF_BYTES : bytes.length;
                    modified = new String(bytes, 0, limit, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    throw new WorkspaceException("Failed to read diff modified for " + normalizedPath, ex);
                }
            }
            String mime = ArtifactMimeTypes.guess(Path.of(normalizedPath).getFileName().toString());
            return new FileDiffResponse(normalizedPath, mime, original, modified, truncated);
        }
    }

    public FileVersionResponse revert(UUID sessionId, String relativePath, String versionId) {
        Path workspace = workspaceRoot(sessionId);
        String normalizedPath = normalizePath(relativePath);
        synchronized (lock(sessionId)) {
            Path historyRoot = historyRoot(workspace);
            FileVersionEntry target = readAllEntries(historyRoot).stream()
                    .filter(entry -> entry.path().equals(normalizedPath))
                    .filter(entry -> entry.versionId().equals(versionId))
                    .findFirst()
                    .orElseThrow(() -> new FileVersionNotFoundException(versionId, normalizedPath));
            try {
                Path file = WorkspacePathGuard.resolve(workspace, normalizedPath);
                if (target.op() == FileVersionOp.CREATED) {
                    Files.deleteIfExists(file);
                } else {
                    Path blob = historyRoot.resolve(BLOBS_DIR).resolve(versionId);
                    if (!Files.isRegularFile(blob)) {
                        throw new FileVersionNotFoundException(versionId, normalizedPath);
                    }
                    Files.createDirectories(file.getParent() != null ? file.getParent() : workspace);
                    Files.copy(blob, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                Map<String, Object> audit = new LinkedHashMap<>();
                audit.put("path", normalizedPath);
                audit.put("versionId", versionId);
                audit.put("op", target.op().wireValue());
                auditLedgerService.record(
                        RunPersistenceContext.forAudit(sessionId, "file-revert"),
                        "file.reverted",
                        audit);
                return FileVersionResponse.from(target);
            } catch (IOException ex) {
                throw new WorkspaceException("Failed to revert " + normalizedPath + " to " + versionId, ex);
            }
        }
    }

    static Path historyRoot(Path workspaceRoot) {
        return workspaceRoot.resolve(HISTORY_DIR).normalize();
    }

    private Path workspaceRoot(UUID sessionId) {
        WorkmateSession session = sessionService.requireSession(sessionId);
        return Path.of(session.getWorkspaceRoot()).toAbsolutePath().normalize();
    }

    private Object lock(UUID sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, ignored -> new Object());
    }

    private static String normalizePath(String relativePath) {
        return relativePath.replace('\\', '/').trim();
    }

    private long nextSeq(Path historyRoot) throws IOException {
        List<FileVersionEntry> entries = readAllEntries(historyRoot);
        return entries.isEmpty() ? 1 : entries.getLast().seq() + 1;
    }

    private void appendIndex(Path historyRoot, FileVersionEntry entry) throws IOException {
        Path index = historyRoot.resolve(INDEX_FILE);
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("seq", entry.seq());
        line.put("path", entry.path());
        line.put("op", entry.op().wireValue());
        line.put("versionId", entry.versionId());
        line.put("ts", entry.ts().toString());
        line.put("runId", entry.runId());
        line.put("bytes", entry.bytes());
        line.put("sha256", entry.sha256());
        String json = objectMapper.writeValueAsString(line);
        Files.writeString(index, json + System.lineSeparator(), StandardCharsets.UTF_8,
                Files.exists(index) ? java.nio.file.StandardOpenOption.APPEND
                        : java.nio.file.StandardOpenOption.CREATE);
    }

    private List<FileVersionEntry> readAllEntries(Path historyRoot) {
        Path index = historyRoot.resolve(INDEX_FILE);
        if (!Files.isRegularFile(index)) {
            return List.of();
        }
        try {
            List<FileVersionEntry> entries = new ArrayList<>();
            for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> raw = objectMapper.readValue(line, new TypeReference<>() {});
                entries.add(new FileVersionEntry(
                        ((Number) raw.get("seq")).longValue(),
                        String.valueOf(raw.get("path")),
                        FileVersionOp.fromWire(String.valueOf(raw.get("op"))),
                        String.valueOf(raw.get("versionId")),
                        Instant.parse(String.valueOf(raw.get("ts"))),
                        raw.get("runId") != null ? String.valueOf(raw.get("runId")) : "",
                        raw.get("bytes") instanceof Number number ? number.longValue() : 0L,
                        raw.get("sha256") != null ? String.valueOf(raw.get("sha256")) : ""));
            }
            return entries;
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to read file history index", ex);
        }
    }

    private static String sha256Hex(byte[] content) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }
}
