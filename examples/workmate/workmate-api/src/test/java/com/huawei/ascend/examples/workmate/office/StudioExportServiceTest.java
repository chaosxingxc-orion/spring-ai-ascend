package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.market.PluginInstallStore;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertWriteRequest;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudioExportServiceTest {

    private static final java.nio.file.Path OFFICE_ROOT =
            java.nio.file.Path.of("../office").toAbsolutePath().normalize();

    @TempDir
    java.nio.file.Path dataDir;

    private StudioExportService exportService;
    private StudioWriteService writeService;

    @BeforeEach
    void setUp() {
        OfficeImportPaths importPaths = new OfficeImportPaths(new WorkmateDataProperties(dataDir.toString()));
        ExpertRegistry expertRegistry = new ExpertRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), importPaths);
        SkillRegistry skillRegistry = new SkillRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), importPaths);
        PlaybookRegistry playbookRegistry = new PlaybookRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), importPaths);
        StudioDraftStore draftStore = new StudioDraftStore(importPaths);
        exportService = new StudioExportService(expertRegistry, skillRegistry, playbookRegistry, draftStore);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var dataProperties = new WorkmateDataProperties(dataDir.toString());
        PluginInstallStore pluginInstallStore = new PluginInstallStore(dataProperties, objectMapper);
        SkillInstallStore skillInstallStore = new SkillInstallStore(pluginInstallStore, dataProperties, objectMapper);
        StudioDraftMetaStore metaStore = new StudioDraftMetaStore(importPaths, objectMapper);
        com.huawei.ascend.examples.workmate.config.WorkmateStudioProperties studioProperties =
                new com.huawei.ascend.examples.workmate.config.WorkmateStudioProperties();
        studioProperties.setAuditEnabled(false);
        StudioDraftLifecycle draftLifecycle = new StudioDraftLifecycle(
                metaStore,
                new StudioAuditService(
                        org.mockito.Mockito.mock(com.huawei.ascend.examples.workmate.audit.AuditLedgerService.class),
                        studioProperties));
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
    void exportsBuiltinOverrideToExpertsSegment() {
        writeService.updateExpert(
                "fund-analyst",
                new StudioExpertWriteRequest(
                        null,
                        "Export Analyst",
                        "Export test",
                        "agent",
                        "Export prompt body.",
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

        var preview = exportService.previewExport();
        assertThat(preview.expertCount()).isEqualTo(1);
        assertThat(preview.items().get(0).suggestedOfficePath()).startsWith("office/experts/fund-analyst");

        byte[] zipBytes = exportService.exportExpertZip("fund-analyst");
        assertThat(readZipPaths(zipBytes))
                .anyMatch(path -> path.startsWith("office/experts/fund-analyst/expert.yaml"));
    }

    @Test
    void exportsAllDraftsZip() {
        writeService.createExpert(new StudioExpertWriteRequest(
                "studio-export-agent",
                "Export Agent",
                "All export",
                "agent",
                "Prompt",
                null,
                "",
                "custom",
                List.of("draft"),
                List.of(),
                List.of(),
                List.of(),
                null,
                Map.of(),
                Map.of()));

        byte[] zipBytes = exportService.exportAllDraftsZip();
        assertThat(readZipPaths(zipBytes)).contains("README.md");
    }

    private static List<String> readZipPaths(byte[] zipBytes) {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            java.util.ArrayList<String> paths = new java.util.ArrayList<>();
            while ((entry = zip.getNextEntry()) != null) {
                paths.add(entry.getName());
            }
            return paths;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
