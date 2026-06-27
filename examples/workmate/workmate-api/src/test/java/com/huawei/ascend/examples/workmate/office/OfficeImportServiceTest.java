package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.office.dto.ExpertImportRequest;
import com.huawei.ascend.examples.workmate.office.dto.SkillUploadRequest;
import com.huawei.ascend.examples.workmate.capability.CapabilityUsageService;
import com.huawei.ascend.examples.workmate.capability.CapabilityUsageStore;
import com.huawei.ascend.examples.workmate.market.PluginInstallStore;
import com.huawei.ascend.examples.workmate.skill.SkillSecurityScanner;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficeImportServiceTest {

    @TempDir
    Path dataDir;

    private ExpertImportService expertImportService;
    private SkillUploadService skillUploadService;
    private ExpertRegistry expertRegistry;
    private SkillRegistry skillRegistry;

    @BeforeEach
    void setUp() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        WorkmateDataProperties dataProperties = new WorkmateDataProperties(dataDir.toString());
        OfficeImportPaths importPaths = new OfficeImportPaths(dataProperties);
        expertRegistry = new ExpertRegistry(new com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties(
                officeRoot.toString()), importPaths);
        skillRegistry = new SkillRegistry(new com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties(
                officeRoot.toString()), importPaths);
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        PluginInstallStore pluginInstallStore = new PluginInstallStore(dataProperties, objectMapper);
        SkillInstallStore installStore = new SkillInstallStore(pluginInstallStore, dataProperties, objectMapper);
        SkillSecurityScanner securityScanner = new SkillSecurityScanner();
        CapabilityUsageService capabilityUsageService =
                new CapabilityUsageService(new CapabilityUsageStore(dataProperties, objectMapper));
        SkillService skillService =
                new SkillService(skillRegistry, installStore, securityScanner, capabilityUsageService);
        expertImportService = new ExpertImportService(importPaths, expertRegistry);
        skillUploadService = new SkillUploadService(
                importPaths,
                skillRegistry,
                skillService,
                new com.huawei.ascend.examples.workmate.market.MarketplaceService(
                        new com.huawei.ascend.examples.workmate.market.MarketplaceStore(
                                dataProperties, objectMapper),
                        pluginInstallStore,
                        skillRegistry,
                        skillService));
    }

    @Test
    void importsCustomExpert() {
        var response = expertImportService.importExpert(new ExpertImportRequest(
                "dogfood-import-expert",
                "Dogfood Expert",
                "Imported for G26 test",
                "agent",
                "custom",
                java.util.List.of("imported"),
                "You are a test expert.",
                "Say hello"));

        assertThat(response.id()).isEqualTo("dogfood-import-expert");
        assertThat(expertRegistry.findExpert("dogfood-import-expert")).isPresent();
        assertThat(Files.exists(dataDir.resolve("office-imports/experts/dogfood-import-expert/expert.yaml")))
                .isTrue();
    }

    @Test
    void uploadsCustomSkill() {
        var response = skillUploadService.uploadSkill(new SkillUploadRequest(
                "dogfood-upload-skill",
                "Dogfood Skill",
                "Uploaded for G26 test",
                "custom",
                java.util.List.of("uploaded"),
                "# Dogfood skill body",
                true));

        assertThat(response.id()).isEqualTo("dogfood-upload-skill");
        assertThat(response.installed()).isTrue();
        assertThat(skillRegistry.findSkill("dogfood-upload-skill")).isPresent();
    }

    @Test
    void importsCustomExpertFromZip() throws Exception {
        String yaml = """
                id: dogfood-zip-expert
                name: Zip Expert
                description: Imported from zip
                expertType: agent
                category: custom
                tags: [imported]
                """;
        byte[] zipBytes;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("expert.yaml"));
            zip.write(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("prompt.md"));
            zip.write("You are a zip expert.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            zipBytes = buffer.toByteArray();
        }

        var response = expertImportService.importExpertZip(new ByteArrayInputStream(zipBytes));

        assertThat(response.id()).isEqualTo("dogfood-zip-expert");
        assertThat(expertRegistry.findExpert("dogfood-zip-expert")).isPresent();
    }

    @Test
    void rejectsInvalidExpertId() {
        assertThatThrownBy(() -> expertImportService.importExpert(new ExpertImportRequest(
                        "Bad ID",
                        "Name",
                        "Desc",
                        "agent",
                        "custom",
                        java.util.List.of(),
                        "prompt",
                        null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
