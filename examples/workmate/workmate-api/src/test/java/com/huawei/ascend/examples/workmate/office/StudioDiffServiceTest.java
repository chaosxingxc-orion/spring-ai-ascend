package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.market.PluginInstallStore;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertWriteRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudioDiffServiceTest {

    private static final Path OFFICE_ROOT = Path.of("../office").toAbsolutePath().normalize();

    @TempDir
    Path dataDir;

    private StudioDiffService diffService;
    private StudioWriteService writeService;
    private StudioDraftStore draftStore;
    private PlaybookRegistry playbookRegistry;

    @BeforeEach
    void setUp() {
        OfficeImportPaths importPaths = new OfficeImportPaths(new WorkmateDataProperties(dataDir.toString()));
        ExpertRegistry expertRegistry = new ExpertRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), importPaths);
        SkillRegistry skillRegistry = new SkillRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), importPaths);
        WelcomeRegistry welcomeRegistry = new WelcomeRegistry(
                new WorkmateOfficeProperties(OFFICE_ROOT.toString()),
                new PlaybookRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), importPaths),
                importPaths);
        draftStore = new StudioDraftStore(importPaths);
        playbookRegistry = new PlaybookRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), importPaths);
        diffService = new StudioDiffService(expertRegistry, skillRegistry, playbookRegistry, welcomeRegistry, draftStore);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var dataProperties = new WorkmateDataProperties(dataDir.toString());
        PluginInstallStore pluginInstallStore = new PluginInstallStore(dataProperties, objectMapper);
        StudioDraftMetaStore metaStore = new StudioDraftMetaStore(importPaths, objectMapper);
        com.huawei.ascend.examples.workmate.config.WorkmateStudioProperties studioProperties =
                new com.huawei.ascend.examples.workmate.config.WorkmateStudioProperties();
        studioProperties.setAuditEnabled(false);
        StudioAuditService auditService = new StudioAuditService(
                org.mockito.Mockito.mock(com.huawei.ascend.examples.workmate.audit.AuditLedgerService.class),
                studioProperties);
        StudioDraftLifecycle draftLifecycle = new StudioDraftLifecycle(metaStore, auditService);
        StudioDraftCoordinator draftCoordinator = new StudioDraftCoordinator(draftLifecycle);
        writeService = new StudioWriteService(
                expertRegistry,
                skillRegistry,
                new SkillInstallStore(pluginInstallStore, dataProperties, objectMapper),
                draftStore,
                org.mockito.Mockito.mock(com.huawei.ascend.examples.workmate.session.SessionService.class),
                org.mockito.Mockito.mock(ExpertImportService.class),
                draftCoordinator);
    }

    @Test
    void detectsPromptDiffAgainstBuiltinBaseline() {
        writeService.updateExpert(
                "fund-analyst",
                new StudioExpertWriteRequest(
                        null,
                        "Studio Draft Analyst",
                        "Updated via studio",
                        "agent",
                        "Draft prompt body for diff test.",
                        null,
                        "",
                        "finance",
                        List.of("draft"),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        Map.of(),
                        Map.of()));

        var diff = diffService.getExpertDiff("fund-analyst");
        assertThat(diff.hasDraft()).isTrue();
        assertThat(diff.hasBaseline()).isTrue();
        assertThat(diff.baselineSource()).isEqualTo(OfficeAssetSource.BUILTIN);
        assertThat(diff.currentSource()).isEqualTo(OfficeAssetSource.DRAFT);
        assertThat(diff.changedFields()).contains("promptContent", "name");
        assertThat(diff.baselinePrompt()).isNotEqualTo(diff.draftPrompt());
    }

    @Test
    void rollbackRemovesDraftOverlay() throws Exception {
        writeService.updateExpert(
                "fund-analyst",
                new StudioExpertWriteRequest(
                        null,
                        "Temporary Draft",
                        "Will rollback",
                        "agent",
                        "Temporary prompt.",
                        null,
                        "",
                        "finance",
                        List.of("draft"),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        Map.of(),
                        Map.of()));
        assertThat(diffService.getExpertDiff("fund-analyst").hasDraft()).isTrue();

        writeService.deleteExpert("fund-analyst");
        assertThat(Files.exists(dataDir.resolve("office-drafts/experts/fund-analyst/expert.yaml"))).isFalse();
        var diff = diffService.getExpertDiff("fund-analyst");
        assertThat(diff.hasDraft()).isFalse();
        assertThat(diff.currentSource()).isEqualTo(OfficeAssetSource.BUILTIN);
    }

    @Test
    void detectsWelcomeYamlDiff() {
        String baseline = draftStore.readBuiltinWelcome(OFFICE_ROOT);
        draftStore.writeWelcomeDraft(baseline.replace("WorkMate，我帮你", "Studio Draft Welcome"));
        var diff = diffService.getWelcomeDiff();
        assertThat(diff.hasDraft()).isTrue();
        assertThat(diff.hasBaseline()).isTrue();
        assertThat(diff.baselineSource()).isEqualTo(OfficeAssetSource.BUILTIN);
        assertThat(diff.currentSource()).isEqualTo(OfficeAssetSource.DRAFT);
        assertThat(diff.changedFields()).contains("welcomeYaml");
        assertThat(diff.baselinePrompt()).isNotEqualTo(diff.draftPrompt());
    }

    @Test
    void detectsPlaybookDiffAgainstBuiltinBaseline() {
        draftStore.writePlaybookDraft(
                "daily-report",
                """
                id: daily-report
                title: Studio Draft Report
                description: Draft playbook
                accent: "#34C759"
                initPrompt: Draft init prompt for playbook diff test.
                placements:
                  - home-best-practice
                """);
        playbookRegistry.reloadAll();
        var diff = diffService.getPlaybookDiff("daily-report");
        assertThat(diff.hasDraft()).isTrue();
        assertThat(diff.hasBaseline()).isTrue();
        assertThat(diff.baselineSource()).isEqualTo(OfficeAssetSource.BUILTIN);
        assertThat(diff.currentSource()).isEqualTo(OfficeAssetSource.DRAFT);
        assertThat(diff.changedFields()).contains("title", "initPrompt");
    }
}
