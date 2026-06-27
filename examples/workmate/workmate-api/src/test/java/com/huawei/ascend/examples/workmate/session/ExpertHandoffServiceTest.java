package com.huawei.ascend.examples.workmate.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.artifact.ArtifactEntry;
import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpertHandoffServiceTest {

    private SessionPersistenceService sessionPersistenceService;
    private ArtifactService artifactService;
    private ExpertService expertService;
    private ExpertHandoffService service;

    @BeforeEach
    void setUp() {
        sessionPersistenceService = mock(SessionPersistenceService.class);
        artifactService = mock(ArtifactService.class);
        expertService = mock(ExpertService.class);
        service = new ExpertHandoffService(sessionPersistenceService, artifactService, expertService);
    }

    @Test
    void switchedEventPayloadIncludesExpertNames() {
        when(expertService.findExpertDefinition("fund-analyst"))
                .thenReturn(Optional.of(new ExpertDefinition(
                        "fund-analyst",
                        "基金分析师",
                        "d",
                        "agent",
                        "prompt",
                        null,
                        "finance",
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        Map.of())));

        Map<String, Object> payload = service.switchedEventPayload(
                "prd-writer", "fund-analyst", 2, "SUMMON_IN_SESSION");

        assertThat(payload.get("fromExpertId")).isEqualTo("prd-writer");
        assertThat(payload.get("toExpertId")).isEqualTo("fund-analyst");
        assertThat(payload.get("toExpertName")).isEqualTo("基金分析师");
        assertThat(payload.get("newGeneration")).isEqualTo(2);
        assertThat(payload.get("mode")).isEqualTo("SUMMON_IN_SESSION");
    }

    @Test
    void buildHandoffPromptSummarizesDialogueAndArtifacts() {
        UUID sessionId = UUID.randomUUID();
        when(sessionPersistenceService.listMessages(sessionId))
                .thenReturn(List.of(
                        Map.of("kind", "user", "text", "写一份 PRD"),
                        Map.of("kind", "assistant", "text", "好的，已开始起草。")));
        when(artifactService.scanWorkspace(any(Path.class)))
                .thenReturn(List.of(new ArtifactEntry(
                        "office/tasks/x/outputs/draft.md", "draft.md", "text/markdown", 120, Instant.now())));
        when(expertService.findExpertDefinition("prd-writer"))
                .thenReturn(Optional.of(new ExpertDefinition(
                        "prd-writer",
                        "PRD 写手",
                        "d",
                        "agent",
                        "prompt",
                        null,
                        "product",
                        List.of("office"),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        "prd-write",
                        Map.of())));
        when(expertService.findExpertDefinition("fund-analyst"))
                .thenReturn(Optional.of(new ExpertDefinition(
                        "fund-analyst",
                        "基金分析师",
                        "d",
                        "agent",
                        "prompt",
                        null,
                        "finance",
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        Map.of())));

        String prompt = service.buildHandoffPrompt(
                sessionId, "prd-writer", "fund-analyst", Path.of("/tmp/ws"));

        assertThat(prompt).contains("PRD 写手");
        assertThat(prompt).contains("基金分析师");
        assertThat(prompt).contains("写一份 PRD");
        assertThat(prompt).contains("office/tasks");
    }
}
