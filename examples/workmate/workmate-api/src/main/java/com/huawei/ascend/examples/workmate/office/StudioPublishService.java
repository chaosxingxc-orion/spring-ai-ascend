package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.StudioDraftMetaResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioReloadResponse;
import org.springframework.stereotype.Service;

@Service
public class StudioPublishService {

    private final StudioDraftStore draftStore;
    private final StudioDraftMetaStore metaStore;
    private final StudioAuditService auditService;
    private final StudioService studioService;

    public StudioPublishService(
            StudioDraftStore draftStore,
            StudioDraftMetaStore metaStore,
            StudioAuditService auditService,
            StudioService studioService) {
        this.draftStore = draftStore;
        this.metaStore = metaStore;
        this.auditService = auditService;
        this.studioService = studioService;
    }

    public StudioDraftMetaResponse publishExpert(String expertId) {
        String safeId = OfficeImportValidator.requireSafeId(expertId, "Expert");
        requireExpertDraft(safeId);
        return publish("expert", safeId);
    }

    public StudioDraftMetaResponse publishSkill(String skillId) {
        String safeId = OfficeImportValidator.requireSafeId(skillId, "Skill");
        if (!draftStore.skillDraftExists(safeId)) {
            throw new IllegalArgumentException("No skill draft to publish: " + safeId);
        }
        return publish("skill", safeId);
    }

    public StudioDraftMetaResponse publishPlaybook(String playbookId) {
        String safeId = OfficeImportValidator.requireSafeId(playbookId, "Playbook");
        if (!draftStore.playbookDraftExists(safeId)) {
            throw new IllegalArgumentException("No playbook draft to publish: " + safeId);
        }
        return publish("playbook", safeId);
    }

    public StudioDraftMetaResponse publishWelcome() {
        if (!draftStore.welcomeDraftExists()) {
            throw new IllegalArgumentException("No welcome draft to publish");
        }
        return publish("welcome", "welcome");
    }

    private StudioDraftMetaResponse publish(String assetType, String assetId) {
        StudioDraftMeta meta = metaStore.markPublished(assetType, assetId);
        StudioReloadResponse reload = studioService.reload();
        auditService.draftPublished(assetType, assetId);
        return StudioDraftMetaResponse.from(meta);
    }

    private void requireExpertDraft(String expertId) {
        if (!draftStore.expertDraftExists(expertId)) {
            throw new IllegalArgumentException("No expert draft to publish: " + expertId);
        }
    }
}
