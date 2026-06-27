package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.market.PluginInstallStore;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillWriteRequest;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionResponse;
import com.huawei.ascend.examples.workmate.session.dto.SessionResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudioWriteServiceTest {

    private static final Path OFFICE_ROOT = Path.of("../office").toAbsolutePath().normalize();

    @TempDir
    Path dataDir;

    private ExpertRegistry expertRegistry;
    private SkillRegistry skillRegistry;
    private StudioWriteService writeService;

    @BeforeEach
    void setUp() {
        OfficeImportPaths importPaths = new OfficeImportPaths(new WorkmateDataProperties(dataDir.toString()));
        expertRegistry = new ExpertRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), importPaths);
        skillRegistry = new SkillRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), importPaths);
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var dataProperties = new WorkmateDataProperties(dataDir.toString());
        PluginInstallStore pluginInstallStore = new PluginInstallStore(dataProperties, objectMapper);
        SkillInstallStore installStore = new SkillInstallStore(pluginInstallStore, dataProperties, objectMapper);
        StudioDraftStore draftStore = new StudioDraftStore(importPaths);
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.createSession(any())).thenAnswer(invocation -> {
            UUID sessionId = UUID.randomUUID();
            return new CreateSessionResponse(
                    new SessionResponse(
                            sessionId,
                            "Studio dry-run: Fund",
                            "/tmp/ws",
                            "ws",
                            SessionStatus.CREATED,
                            "fund-analyst",
                            null,
                            null,
                            0L,
                            0L,
                            null,
                            null,
                            false,
                            null,
                            null,
                            null,
                            null,
                            List.of(),
                            List.of()),
                    List.of());
        });
        StudioDraftMetaStore metaStore = new StudioDraftMetaStore(importPaths, objectMapper);
        com.huawei.ascend.examples.workmate.config.WorkmateStudioProperties studioProperties =
                new com.huawei.ascend.examples.workmate.config.WorkmateStudioProperties();
        studioProperties.setAuditEnabled(false);
        StudioDraftLifecycle draftLifecycle = new StudioDraftLifecycle(
                metaStore,
                new StudioAuditService(mock(com.huawei.ascend.examples.workmate.audit.AuditLedgerService.class), studioProperties));
        StudioDraftCoordinator draftCoordinator = new StudioDraftCoordinator(draftLifecycle);
        writeService = new StudioWriteService(
                expertRegistry,
                skillRegistry,
                installStore,
                draftStore,
                sessionService,
                mock(ExpertImportService.class),
                draftCoordinator);
    }

    @Test
    void createsNewAgentDraft() {
        var response = writeService.createExpert(new StudioExpertWriteRequest(
                "studio-new-agent",
                "Studio New Agent",
                "Created in studio test",
                "agent",
                "You are a studio test agent.",
                null,
                "Say hello",
                "custom",
                List.of("draft"),
                List.of(),
                List.of(),
                List.of(),
                25,
                Map.of("zh", "测试助手"),
                Map.of("zh", "测试员")));

        assertThat(response.summary().id()).isEqualTo("studio-new-agent");
        assertThat(response.source()).isEqualTo(OfficeAssetSource.DRAFT);
        assertThat(response.promptContent()).contains("studio test agent");
        assertThat(expertRegistry.requireExpert("studio-new-agent").maxTurns()).isEqualTo(25);
    }

    @Test
    void rejectsDuplicateExpertOnCreate() {
        writeService.createExpert(sampleExpertRequest("studio-dup-agent", "First", "Prompt one"));
        assertThatThrownBy(() -> writeService.createExpert(sampleExpertRequest("studio-dup-agent", "Second", "Prompt two")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updatesBuiltinExpertAsDraftOverride() {
        var response = writeService.updateExpert(
                "fund-analyst",
                new StudioExpertWriteRequest(
                        null,
                        "Overridden Fund Analyst",
                        "Draft override of builtin",
                        "agent",
                        "Overridden fund analyst prompt.",
                        null,
                        "",
                        "finance",
                        List.of("draft"),
                        List.of("qieman"),
                        List.of(),
                        List.of(),
                        40,
                        Map.of(),
                        Map.of()));

        assertThat(response.source()).isEqualTo(OfficeAssetSource.DRAFT);
        assertThat(response.summary().name()).isEqualTo("Overridden Fund Analyst");
        assertThat(expertRegistry.findEntry("fund-analyst").orElseThrow().source())
                .isEqualTo(OfficeAssetSource.DRAFT);
    }

    @Test
    void deletesDraftAndRestoresBuiltin() {
        writeService.updateExpert("fund-analyst", sampleExpertRequest(null, "Draft Name", "Draft prompt"));
        assertThat(expertRegistry.findEntry("fund-analyst").orElseThrow().source())
                .isEqualTo(OfficeAssetSource.DRAFT);

        writeService.deleteExpert("fund-analyst");

        assertThat(expertRegistry.findEntry("fund-analyst").orElseThrow().source())
                .isEqualTo(OfficeAssetSource.BUILTIN);
        assertThat(expertRegistry.requireExpert("fund-analyst").name()).isEqualTo("基金研究助手");
    }

    @Test
    void rejectsDeleteWhenNoDraft() {
        assertThatThrownBy(() -> writeService.deleteExpert("fund-analyst"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No draft to delete");
    }

    @Test
    void forksExistingExpertToDraft() {
        var response = writeService.forkExpert("fund-analyst");
        assertThat(response.source()).isEqualTo(OfficeAssetSource.DRAFT);
        assertThat(response.summary().id()).isEqualTo("fund-analyst");
        assertThat(response.promptContent()).contains("基金研究专家");
    }

    @Test
    void rejectsTeamExpertTypeInPhase1() {
        assertThatThrownBy(() -> writeService.createExpert(new StudioExpertWriteRequest(
                        "studio-team",
                        "Team",
                        "Desc",
                        "team",
                        "Lead prompt",
                        null,
                        "",
                        "custom",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        Map.of(),
                        Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phase 1");
    }

    @Test
    void createsAndUpdatesSkillDraft() {
        var created = writeService.createSkill(new StudioSkillWriteRequest(
                "studio-new-skill",
                "Studio Skill",
                "Skill description",
                "custom",
                List.of("draft"),
                "# Skill body",
                null,
                "draft",
                false));
        assertThat(created.source()).isEqualTo(OfficeAssetSource.DRAFT);

        var updated = writeService.updateSkill(
                "studio-new-skill",
                new StudioSkillWriteRequest(
                        null,
                        "Studio Skill Updated",
                        "Updated description",
                        "custom",
                        List.of("draft"),
                        "# Updated body",
                        null,
                        "draft",
                        false));
        assertThat(updated.summary().name()).isEqualTo("Studio Skill Updated");
        assertThat(updated.skillContent()).contains("Updated body");
    }

    @Test
    void validateExpertReturnsStructuredFailure() {
        var result = writeService.validateExpert(
                new StudioExpertWriteRequest(
                        "bad id",
                        "",
                        "Desc",
                        "agent",
                        "",
                        null,
                        "",
                        "custom",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        Map.of(),
                        Map.of()),
                null);
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isNotBlank();
    }

    private static StudioExpertWriteRequest sampleExpertRequest(String id, String name, String prompt) {
        return new StudioExpertWriteRequest(
                id,
                name,
                "Description",
                "agent",
                prompt,
                null,
                "",
                "custom",
                List.of("draft"),
                List.of(),
                List.of(),
                List.of(),
                null,
                Map.of(),
                Map.of());
    }
}
