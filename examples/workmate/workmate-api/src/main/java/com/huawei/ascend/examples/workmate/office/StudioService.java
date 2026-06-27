package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.StudioExpertListItemResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioPlaybookListItemResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioPlaybookSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioReloadResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillListItemResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillSourceResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StudioService {

    private final ExpertRegistry expertRegistry;
    private final SkillRegistry skillRegistry;
    private final SkillInstallStore skillInstallStore;
    private final PlaybookRegistry playbookRegistry;
    private final WelcomeRegistry welcomeRegistry;
    private final StudioAuditService auditService;

    public StudioService(
            ExpertRegistry expertRegistry,
            SkillRegistry skillRegistry,
            SkillInstallStore skillInstallStore,
            PlaybookRegistry playbookRegistry,
            WelcomeRegistry welcomeRegistry,
            StudioAuditService auditService) {
        this.expertRegistry = expertRegistry;
        this.skillRegistry = skillRegistry;
        this.skillInstallStore = skillInstallStore;
        this.playbookRegistry = playbookRegistry;
        this.welcomeRegistry = welcomeRegistry;
        this.auditService = auditService;
    }

    public StudioReloadResponse reload() {
        int experts = expertRegistry.reloadAll();
        int skills = skillRegistry.reloadAll();
        playbookRegistry.reloadAll();
        welcomeRegistry.reloadAll();
        List<String> warnings = new ArrayList<>();
        warnings.addAll(expertRegistry.reloadWarnings());
        warnings.addAll(skillRegistry.reloadWarnings());
        StudioReloadResponse response = StudioReloadResponse.from(new OfficeReloadResult(experts, skills, List.copyOf(warnings)));
        auditService.reload(response.experts(), response.skills());
        return response;
    }

    public List<StudioExpertListItemResponse> listExperts() {
        return expertRegistry.listEntries().stream().map(StudioExpertListItemResponse::from).toList();
    }

    public List<StudioSkillListItemResponse> listSkills() {
        return skillRegistry.listEntries().stream()
                .map(entry -> StudioSkillListItemResponse.from(entry, skillInstallStore.isInstalled(entry.definition().id())))
                .toList();
    }

    public StudioExpertSourceResponse getExpertSource(String expertId) {
        ExpertRegistryEntry entry =
                expertRegistry.findEntry(expertId).orElseThrow(() -> new ExpertNotFoundException(expertId));
        return StudioExpertSourceResponse.from(entry);
    }

    public StudioSkillSourceResponse getSkillSource(String skillId) {
        SkillRegistryEntry entry =
                skillRegistry.findEntry(skillId).orElseThrow(() -> new SkillNotFoundException(skillId));
        boolean installed = skillInstallStore.isInstalled(skillId);
        return StudioSkillSourceResponse.from(entry, installed);
    }

    public List<StudioPlaybookListItemResponse> listPlaybooks() {
        return playbookRegistry.listEntries().stream().map(StudioPlaybookListItemResponse::from).toList();
    }

    public StudioPlaybookSourceResponse getPlaybookSource(String playbookId) {
        PlaybookRegistryEntry entry =
                playbookRegistry.findEntry(playbookId).orElseThrow(() -> new PlaybookNotFoundException(playbookId));
        return StudioPlaybookSourceResponse.from(entry);
    }
}
