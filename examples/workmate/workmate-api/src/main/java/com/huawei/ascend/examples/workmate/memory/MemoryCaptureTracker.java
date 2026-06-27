package com.huawei.ascend.examples.workmate.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MemoryCaptureTracker {

    private static final String CAPTURED_FILE = "captured_sessions.jsonl";

    private final Path capturedPath;
    private final ObjectMapper objectMapper;
    private final Set<String> capturedSessionIds = new HashSet<>();

    public MemoryCaptureTracker(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.capturedPath = dataProperties.resolvedPath().resolve("memory").resolve(CAPTURED_FILE);
        this.objectMapper = objectMapper;
        loadExisting();
    }

    public synchronized boolean alreadyCaptured(UUID sessionId) {
        return capturedSessionIds.contains(sessionId.toString());
    }

    public synchronized void markCaptured(UUID sessionId) {
        String id = sessionId.toString();
        if (capturedSessionIds.contains(id)) {
            return;
        }
        capturedSessionIds.add(id);
        try {
            Files.createDirectories(capturedPath.getParent());
            String line = objectMapper.writeValueAsString(new CapturedEntry(id, java.time.Instant.now().toString()));
            Files.writeString(
                    capturedPath,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to record memory capture for session " + id, ex);
        }
    }

    public synchronized void clearAll() {
        capturedSessionIds.clear();
        try {
            Files.deleteIfExists(capturedPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to clear memory capture tracker", ex);
        }
    }

    private void loadExisting() {
        if (!Files.isRegularFile(capturedPath)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(capturedPath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                CapturedEntry entry = objectMapper.readValue(line, CapturedEntry.class);
                if (entry.sessionId() != null) {
                    capturedSessionIds.add(entry.sessionId());
                }
            }
        } catch (IOException ignored) {
            // start fresh if tracker file is corrupt
        }
    }

    private record CapturedEntry(String sessionId, String capturedAt) {}
}
