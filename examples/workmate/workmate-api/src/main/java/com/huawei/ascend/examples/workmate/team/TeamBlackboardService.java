package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.session.WorkspaceException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class TeamBlackboardService {

    private static final int PREVIEW_MAX = 200;

    private final ConcurrentHashMap<String, ReentrantLock> writeLocks = new ConcurrentHashMap<>();

    public record MemoryUpdate(
            String relativePath,
            String section,
            String preview,
            int totalBytes,
            String action,
            long version) {}

    public MemoryUpdate initialize(Path workspaceRoot, String parentRunId, String teamId, String userRequest) {
        return withLock(parentRunId, () -> {
            String relativePath = TeamBlackboardContract.relativePath(parentRunId);
            Path file = workspaceRoot.resolve(relativePath);
            try {
                Files.createDirectories(file.getParent());
                String body = """
                        # Team blackboard

                        - parentRunId: %s
                        - teamId: %s
                        - startedAt: %s

                        ## User request

                        %s

                        ---
                        """.formatted(parentRunId, teamId, Instant.now(), userRequest.trim());
                Files.writeString(file, body, StandardCharsets.UTF_8);
                long version = writeMeta(workspaceRoot, parentRunId, 1);
                return new MemoryUpdate(relativePath, "init", preview(body), body.length(), "init", version);
            } catch (IOException ex) {
                throw new WorkspaceException("Failed to initialize team blackboard at " + relativePath, ex);
            }
        });
    }

    public MemoryUpdate append(Path workspaceRoot, String parentRunId, String section, String content) {
        return appendLocked(workspaceRoot, parentRunId, section, content);
    }

    /** Locked append with monotonic version (W27 shared-state write conflict guard). */
    public MemoryUpdate appendLocked(Path workspaceRoot, String parentRunId, String section, String content) {
        return withLock(parentRunId, () -> {
            String relativePath = TeamBlackboardContract.relativePath(parentRunId);
            Path file = workspaceRoot.resolve(relativePath);
            String entry = """
                    ## %s (%s)

                    %s

                    ---
                    """.formatted(section, Instant.now(), content != null ? content.trim() : "");
            try {
                if (!Files.exists(file)) {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, "# Team blackboard\n\n", StandardCharsets.UTF_8);
                }
                Files.writeString(file, entry, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
                String full = Files.readString(file, StandardCharsets.UTF_8);
                long version = bumpVersion(workspaceRoot, parentRunId);
                return new MemoryUpdate(
                        relativePath, section, preview(content), full.length(), "append", version);
            } catch (IOException ex) {
                throw new WorkspaceException("Failed to append team blackboard at " + relativePath, ex);
            }
        });
    }

    public boolean containsContent(Path workspaceRoot, String parentRunId, String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        String normalized = content.trim();
        String board = read(workspaceRoot, parentRunId);
        return board.contains(normalized);
    }

    public long currentVersion(Path workspaceRoot, String parentRunId) {
        return readMeta(workspaceRoot, parentRunId).version();
    }

    public String readForPrompt(Path workspaceRoot, String parentRunId, int maxChars) {
        String full = read(workspaceRoot, parentRunId);
        if (full.length() <= maxChars) {
            return full;
        }
        return full.substring(0, maxChars) + "\n\n…(blackboard truncated for prompt)";
    }

    public String read(Path workspaceRoot, String parentRunId) {
        String relativePath = TeamBlackboardContract.relativePath(parentRunId);
        Path file = workspaceRoot.resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to read team blackboard at " + relativePath, ex);
        }
    }

    public Map<String, Object> memoryPayload(String parentRunId, MemoryUpdate update) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parentRunId", parentRunId);
        payload.put("path", update.relativePath());
        payload.put("section", update.section());
        payload.put("preview", update.preview());
        payload.put("totalBytes", update.totalBytes());
        payload.put("action", update.action());
        payload.put("version", update.version());
        return payload;
    }

    private MemoryUpdate withLock(String parentRunId, java.util.function.Supplier<MemoryUpdate> action) {
        ReentrantLock lock = writeLocks.computeIfAbsent(parentRunId, id -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private long bumpVersion(Path workspaceRoot, String parentRunId) {
        TeamBlackboardMeta meta = readMeta(workspaceRoot, parentRunId);
        long next = meta.version() + 1;
        writeMeta(workspaceRoot, parentRunId, next);
        return next;
    }

    private TeamBlackboardMeta readMeta(Path workspaceRoot, String parentRunId) {
        String metaPath = TeamBlackboardContract.metaRelativePath(parentRunId);
        Path file = workspaceRoot.resolve(metaPath);
        if (!Files.isRegularFile(file)) {
            return new TeamBlackboardMeta(0, Instant.EPOCH);
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return new TeamBlackboardMeta(0, Instant.EPOCH);
            }
            // minimal JSON: {"version":N,"updatedAt":"..."}
            long version = 0;
            int vIdx = raw.indexOf("\"version\"");
            if (vIdx >= 0) {
                int colon = raw.indexOf(':', vIdx);
                int end = raw.indexOf(',', colon);
                if (end < 0) {
                    end = raw.indexOf('}', colon);
                }
                if (colon > 0 && end > colon) {
                    version = Long.parseLong(raw.substring(colon + 1, end).trim());
                }
            }
            return new TeamBlackboardMeta(version, Instant.now());
        } catch (IOException | NumberFormatException ex) {
            return new TeamBlackboardMeta(0, Instant.EPOCH);
        }
    }

    private long writeMeta(Path workspaceRoot, String parentRunId, long version) {
        String metaPath = TeamBlackboardContract.metaRelativePath(parentRunId);
        Path file = workspaceRoot.resolve(metaPath);
        try {
            Files.createDirectories(file.getParent());
            String json = "{\"version\":" + version + ",\"updatedAt\":\"" + Instant.now() + "\"}";
            Files.writeString(file, json, StandardCharsets.UTF_8);
            return version;
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to write blackboard meta at " + metaPath, ex);
        }
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= PREVIEW_MAX) {
            return trimmed;
        }
        return trimmed.substring(0, PREVIEW_MAX) + "…";
    }
}
