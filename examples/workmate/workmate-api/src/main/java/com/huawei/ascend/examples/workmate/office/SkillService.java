package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.capability.CapabilityUsageService;
import com.huawei.ascend.examples.workmate.office.dto.SkillSummaryResponse;
import com.huawei.ascend.examples.workmate.skill.SkillScanResult;
import com.huawei.ascend.examples.workmate.skill.SkillSecurityScanner;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SkillService {

    private final SkillRegistry registry;
    private final SkillInstallStore installStore;
    private final SkillSecurityScanner securityScanner;
    private final CapabilityUsageService capabilityUsageService;

    public SkillService(
            SkillRegistry registry,
            SkillInstallStore installStore,
            SkillSecurityScanner securityScanner,
            CapabilityUsageService capabilityUsageService) {
        this.registry = registry;
        this.installStore = installStore;
        this.securityScanner = securityScanner;
        this.capabilityUsageService = capabilityUsageService;
    }

    @PostConstruct
    void seedDefaultInstalls() {
        installStore.ensureDefaults(registry);
    }

    public List<SkillSummaryResponse> listSkills() {
        return registry.listSkills().stream()
                .map(skill -> SkillSummaryResponse.from(skill, installStore.isInstalled(skill.id())))
                .toList();
    }

    public SkillSummaryResponse getSkill(String skillId) {
        SkillDefinition skill = registry.requireSkill(skillId);
        return SkillSummaryResponse.from(skill, installStore.isInstalled(skillId));
    }

    public SkillScanResult scanSkill(String skillId) {
        SkillDefinition skill = registry.requireSkill(skillId);
        return securityScanner.scan(skill);
    }

    public SkillSummaryResponse install(String skillId) {
        SkillDefinition skill = registry.requireSkill(skillId);
        installStore.install(skillId);
        capabilityUsageService.recordUsage("skill", skillId);
        return SkillSummaryResponse.from(skill, true);
    }

    public SkillSummaryResponse uninstall(String skillId) {
        SkillDefinition skill = registry.requireSkill(skillId);
        if (skill.defaultInstalled()) {
            throw new IllegalStateException("Policy locked skill cannot be removed: " + skillId);
        }
        installStore.uninstall(skillId);
        return SkillSummaryResponse.from(skill, false);
    }
}
