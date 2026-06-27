package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillWriteRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudioDraftStoreTest {

    @TempDir
    Path dataDir;

    private StudioDraftStore draftStore;

    @BeforeEach
    void setUp() {
        draftStore = new StudioDraftStore(new OfficeImportPaths(new WorkmateDataProperties(dataDir.toString())));
    }

    @Test
    void writesAndDetectsExpertDraft() {
        ExpertDefinition expert = new ExpertDefinition(
                "draft-agent",
                "Draft Agent",
                "For store test",
                "agent",
                "You are a draft agent.",
                "",
                "custom",
                java.util.List.of("draft"),
                java.util.List.of(),
                java.util.List.of(),
                null,
                null,
                null,
                null,
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                20,
                java.util.List.of(),
                java.util.List.of(),
                null,
                null);

        assertThat(draftStore.expertDraftExists("draft-agent")).isFalse();
        draftStore.writeExpertDraft("draft-agent", expert, "prompt.md", expert.systemPrompt());
        assertThat(draftStore.expertDraftExists("draft-agent")).isTrue();
        assertThat(Files.exists(draftStore.expertDraftDir("draft-agent").resolve("expert.yaml"))).isTrue();
        assertThat(Files.exists(draftStore.expertDraftDir("draft-agent").resolve("prompt.md"))).isTrue();
    }

    @Test
    void deletesExpertDraft() {
        ExpertDefinition expert = new ExpertDefinition(
                "to-delete",
                "Delete Me",
                "Desc",
                "agent",
                "Prompt",
                "",
                "custom",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                null,
                null,
                null,
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                null,
                java.util.List.of(),
                java.util.List.of(),
                null,
                null);
        draftStore.writeExpertDraft("to-delete", expert, "prompt.md", "Prompt");
        draftStore.deleteExpertDraft("to-delete");
        assertThat(draftStore.expertDraftExists("to-delete")).isFalse();
    }

    @Test
    void writesSkillDraft() {
        SkillDefinition skill = new SkillDefinition(
                "draft-skill", "Draft Skill", "Desc", "custom", java.util.List.of("draft"), "draft", false, "# Body");
        draftStore.writeSkillDraft("draft-skill", skill, "SKILL.md");
        assertThat(draftStore.skillDraftExists("draft-skill")).isTrue();
        assertThat(Files.exists(draftStore.skillDraftDir("draft-skill").resolve("skill.yaml"))).isTrue();
    }
}
