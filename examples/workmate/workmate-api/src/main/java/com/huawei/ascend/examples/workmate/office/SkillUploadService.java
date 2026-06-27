package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.market.MarketplaceService;
import com.huawei.ascend.examples.workmate.office.dto.ImportValidationResponse;
import com.huawei.ascend.examples.workmate.office.dto.SkillSummaryResponse;
import com.huawei.ascend.examples.workmate.office.dto.SkillUploadRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SkillUploadService {

    private static final String SKILL_FILE = "SKILL.md";

    private final OfficeImportPaths importPaths;
    private final SkillRegistry skillRegistry;
    private final SkillService skillService;
    private final MarketplaceService marketplaceService;

    public SkillUploadService(
            OfficeImportPaths importPaths,
            SkillRegistry skillRegistry,
            SkillService skillService,
            MarketplaceService marketplaceService) {
        this.importPaths = importPaths;
        this.skillRegistry = skillRegistry;
        this.skillService = skillService;
        this.marketplaceService = marketplaceService;
    }

    public ImportValidationResponse validate(SkillUploadRequest request) {
        try {
            normalize(request);
            return new ImportValidationResponse(true, "OK");
        } catch (IllegalArgumentException ex) {
            return new ImportValidationResponse(false, ex.getMessage());
        }
    }

    public SkillSummaryResponse uploadSkill(SkillUploadRequest request) {
        SkillUploadRequest normalized = normalize(request);
        if (skillRegistry.findSkill(normalized.id()).isPresent()) {
            throw new IllegalArgumentException("Skill already exists: " + normalized.id());
        }
        Path skillDir = importPaths.skillDir(normalized.id());
        try {
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve(SKILL_FILE), normalized.skillContent() + System.lineSeparator());
            Files.writeString(skillDir.resolve("skill.yaml"), renderSkillYaml(normalized));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write uploaded skill " + normalized.id(), ex);
        }
        skillRegistry.reloadImports(importPaths.skillsDir());
        marketplaceService.refreshMarketplace("workmate-builtin");
        if (normalized.install()) {
            return skillService.install(normalized.id());
        }
        return skillService.getSkill(normalized.id());
    }

    private SkillUploadRequest normalize(SkillUploadRequest request) {
        String id = OfficeImportValidator.requireSafeId(request.id(), "Skill");
        String name = OfficeImportValidator.requireText(request.name(), "Skill name");
        String description = OfficeImportValidator.requireText(request.description(), "Skill description");
        String category = request.category() == null || request.category().isBlank()
                ? "custom"
                : request.category().trim();
        List<String> tags = request.tags() == null ? List.of("uploaded") : List.copyOf(request.tags());
        String content = OfficeImportValidator.requireText(request.skillContent(), "Skill content");
        return new SkillUploadRequest(id, name, description, category, tags, content, request.install());
    }

    private static String renderSkillYaml(SkillUploadRequest request) {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("id", request.id());
        yaml.put("name", request.name());
        yaml.put("description", request.description());
        yaml.put("category", request.category());
        yaml.put("tags", request.tags());
        yaml.put("source", "uploaded");
        yaml.put("defaultInstalled", false);
        yaml.put("skillFile", SKILL_FILE);
        org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
        options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new org.yaml.snakeyaml.Yaml(options).dump(yaml);
    }
}
