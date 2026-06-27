package com.huawei.ascend.examples.workmate.memory;

import com.huawei.ascend.examples.workmate.config.WorkmateMemoryProperties;
import com.huawei.ascend.examples.workmate.memory.MemorySettingsStore.MemorySettings;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryStore memoryStore;
    private final MemorySettingsStore settingsStore;
    private final MemorySummarizer summarizer;
    private final MemoryRedactor redactor;
    private final MemoryCaptureTracker captureTracker;
    private final SessionPersistenceService sessionPersistenceService;
    private final WorkmateMemoryProperties properties;

    public MemoryService(
            MemoryStore memoryStore,
            MemorySettingsStore settingsStore,
            MemorySummarizer summarizer,
            MemoryRedactor redactor,
            MemoryCaptureTracker captureTracker,
            SessionPersistenceService sessionPersistenceService,
            WorkmateMemoryProperties properties) {
        this.memoryStore = memoryStore;
        this.settingsStore = settingsStore;
        this.summarizer = summarizer;
        this.redactor = redactor;
        this.captureTracker = captureTracker;
        this.sessionPersistenceService = sessionPersistenceService;
        this.properties = properties;
    }

    public MemoryStatus status() {
        MemorySettings settings = settingsStore.read();
        String content = memoryStore.read(ownerId());
        String injectPreview = settings.enabled() ? truncate(content, properties.maxInjectChars()) : "";
        return new MemoryStatus(
                settings.enabled(),
                settings.autoCapture(),
                ownerId(),
                content,
                injectPreview,
                !content.isBlank(),
                content.length());
    }

    public MemorySettings updateSettings(MemorySettings settings) {
        return settingsStore.save(settings);
    }

    public String loadForInjection() {
        if (!settingsStore.read().enabled()) {
            return "";
        }
        return truncate(memoryStore.read(ownerId()), properties.maxInjectChars());
    }

    public CaptureResult captureFromSession(UUID sessionId, boolean allowDuplicate) {
        if (!settingsStore.read().enabled()) {
            return CaptureResult.skipped("memory disabled");
        }
        if (!allowDuplicate && captureTracker.alreadyCaptured(sessionId)) {
            return CaptureResult.skipped("already captured");
        }
        String transcript = buildTranscript(sessionId);
        if (transcript.isBlank()) {
            return CaptureResult.skipped("empty transcript");
        }
        String existing = memoryStore.read(ownerId());
        List<String> entries = redactor.sanitizeEntries(summarizer.extract(transcript, existing));
        if (entries.isEmpty()) {
            return CaptureResult.noOp();
        }
        memoryStore.append(ownerId(), entries);
        captureTracker.markCaptured(sessionId);
        LOG.info("Captured {} memory entries for session {}", entries.size(), sessionId);
        return CaptureResult.success(entries);
    }

    public void tryAutoCapture(UUID sessionId) {
        MemorySettings settings = settingsStore.read();
        if (!settings.enabled() || !settings.autoCapture()) {
            return;
        }
        try {
            captureFromSession(sessionId, false);
        } catch (Exception ex) {
            LOG.warn("Auto memory capture failed sessionId={}", sessionId, ex);
        }
    }

    public void clearMemory() {
        memoryStore.clear(ownerId());
        captureTracker.clearAll();
    }

    private String buildTranscript(UUID sessionId) {
        List<Map<String, Object>> messages = sessionPersistenceService.listMessages(sessionId);
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            String kind = String.valueOf(message.getOrDefault("kind", ""));
            if ("user".equals(kind)) {
                Object text = message.get("text");
                if (text != null && !text.toString().isBlank()) {
                    lines.add("user: " + text);
                }
            } else if ("assistant".equals(kind)) {
                Object text = message.get("text");
                if (text != null && !text.toString().isBlank()) {
                    String assistant = text.toString();
                    if (assistant.length() > 800) {
                        assistant = assistant.substring(0, 797) + "…";
                    }
                    lines.add("assistant: " + assistant);
                }
            }
        }
        return String.join("\n", lines);
    }

    private String ownerId() {
        return properties.ownerId();
    }

    static String truncate(String content, int maxChars) {
        if (content == null || content.isBlank() || maxChars <= 0) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars - 1) + "…";
    }

    public record MemoryStatus(
            boolean enabled,
            boolean autoCapture,
            String ownerId,
            String content,
            String injectPreview,
            boolean hasContent,
            int charCount) {}

    public record CaptureResult(String status, List<String> entries, String reason) {
        public static CaptureResult success(List<String> entries) {
            return new CaptureResult("captured", entries, null);
        }

        public static CaptureResult noOp() {
            return new CaptureResult("no-op", List.of(), "nothing to capture");
        }

        public static CaptureResult skipped(String reason) {
            return new CaptureResult("skipped", List.of(), reason);
        }
    }
}
