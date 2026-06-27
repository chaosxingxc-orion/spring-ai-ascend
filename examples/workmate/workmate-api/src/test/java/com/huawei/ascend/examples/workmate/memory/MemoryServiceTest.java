package com.huawei.ascend.examples.workmate.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateMemoryProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoryServiceTest {

    @TempDir
    Path tempDir;

    private MemoryStore memoryStore;
    private MemorySettingsStore settingsStore;
    private MemoryCaptureTracker captureTracker;
    private SessionPersistenceService sessionPersistenceService;
    private MemorySummarizer summarizer;
    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        WorkmateDataProperties dataProperties = new WorkmateDataProperties(tempDir.toString());
        ObjectMapper objectMapper = new ObjectMapper();
        memoryStore = new MemoryStore(dataProperties);
        settingsStore = new MemorySettingsStore(dataProperties, objectMapper);
        captureTracker = new MemoryCaptureTracker(dataProperties, objectMapper);
        sessionPersistenceService = mock(SessionPersistenceService.class);
        summarizer = mock(MemorySummarizer.class);
        MemoryRedactor redactor = new MemoryRedactor();
        memoryService = new MemoryService(
                memoryStore,
                settingsStore,
                summarizer,
                redactor,
                captureTracker,
                sessionPersistenceService,
                new WorkmateMemoryProperties("default", 20, false));
    }

    @Test
    void loadForInjectionDisabledByDefault() {
        assertThat(memoryService.loadForInjection()).isBlank();
    }

    @Test
    void loadForInjectionTruncatesWhenEnabled() {
        settingsStore.save(new MemorySettingsStore.MemorySettings(true, false));
        memoryStore.append("default", List.of("012345678901234567890"));

        String injected = memoryService.loadForInjection();

        assertThat(injected).hasSize(20);
        assertThat(injected).endsWith("…");
    }

    @Test
    void captureSkipsSecretLikeEntries() throws Exception {
        settingsStore.save(new MemorySettingsStore.MemorySettings(true, false));
        UUID sessionId = UUID.randomUUID();
        when(sessionPersistenceService.listMessages(sessionId))
                .thenReturn(List.of(Map.of("kind", "user", "text", "my api_key is secret")));
        when(summarizer.extract(anyString(), anyString())).thenReturn(List.of("password: hunter2"));

        MemoryService.CaptureResult result = memoryService.captureFromSession(sessionId, true);

        assertThat(result.status()).isEqualTo("no-op");
        assertThat(Files.exists(memoryStore.memoryFile("default"))).isFalse();
    }

    @Test
    void capturePersistsSanitizedEntries() {
        settingsStore.save(new MemorySettingsStore.MemorySettings(true, false));
        UUID sessionId = UUID.randomUUID();
        when(sessionPersistenceService.listMessages(sessionId))
                .thenReturn(List.of(Map.of("kind", "user", "text", "I prefer concise replies")));
        when(summarizer.extract(anyString(), eq(""))).thenReturn(List.of("Prefers concise replies"));

        MemoryService.CaptureResult result = memoryService.captureFromSession(sessionId, true);

        assertThat(result.status()).isEqualTo("captured");
        assertThat(memoryStore.read("default")).contains("Prefers concise replies");
        assertThat(captureTracker.alreadyCaptured(sessionId)).isTrue();
    }

    @Test
    void autoCaptureSkipsDuplicateSession() {
        settingsStore.save(new MemorySettingsStore.MemorySettings(true, true));
        UUID sessionId = UUID.randomUUID();
        captureTracker.markCaptured(sessionId);
        when(sessionPersistenceService.listMessages(sessionId))
                .thenReturn(List.of(Map.of("kind", "user", "text", "hello")));

        memoryService.tryAutoCapture(sessionId);

        assertThat(memoryStore.read("default")).isBlank();
    }
}
