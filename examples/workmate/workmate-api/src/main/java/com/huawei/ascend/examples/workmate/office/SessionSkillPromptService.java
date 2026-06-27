package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.capability.CapabilityUsageService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SessionSkillPromptService {

    private static final int MAX_SKILL_BODY_CHARS = 4000;
    private static final int MAX_TOTAL_CHARS = 12000;

    private final SkillRegistry skillRegistry;
    private final SkillInstallStore installStore;
    private final CapabilityUsageService capabilityUsageService;

    public SessionSkillPromptService(
            SkillRegistry skillRegistry,
            SkillInstallStore installStore,
            CapabilityUsageService capabilityUsageService) {
        this.skillRegistry = skillRegistry;
        this.installStore = installStore;
        this.capabilityUsageService = capabilityUsageService;
    }

    public String buildPromptSection(List<String> enabledSkillIds) {
        if (enabledSkillIds == null || enabledSkillIds.isEmpty()) {
            return "";
        }
        List<String> blocks = new ArrayList<>();
        int totalChars = 0;
        for (String skillId : enabledSkillIds) {
            SkillDefinition skill = skillRegistry.findSkill(skillId).orElse(null);
            if (skill == null || !isAvailable(skill.id())) {
                continue;
            }
            capabilityUsageService.recordUsage("skill", skill.id());
            String body = skill.skillBody() != null && !skill.skillBody().isBlank()
                    ? skill.skillBody()
                    : skill.description();
            if (body == null || body.isBlank()) {
                continue;
            }
            if (body.length() > MAX_SKILL_BODY_CHARS) {
                body = body.substring(0, MAX_SKILL_BODY_CHARS - 1) + "…";
            }
            String block = "### Skill: %s (%s)\n%s".formatted(skill.name(), skill.id(), body);
            if (totalChars + block.length() > MAX_TOTAL_CHARS) {
                break;
            }
            blocks.add(block);
            totalChars += block.length();
        }
        if (blocks.isEmpty()) {
            return "";
        }
        return """
                Session-enabled skills. Follow their instructions when relevant:

                %s
                """.formatted(String.join("\n\n", blocks));
    }

    private boolean isAvailable(String skillId) {
        if (installStore.isInstalled(skillId)) {
            return true;
        }
        return skillRegistry.findSkill(skillId).map(SkillDefinition::defaultInstalled).orElse(false);
    }
}
