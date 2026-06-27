package com.huawei.ascend.examples.workmate.office;

import org.springframework.stereotype.Service;

@Service
public class StudioDraftLifecycle {

    private final StudioDraftMetaStore metaStore;
    private final StudioAuditService auditService;

    public StudioDraftLifecycle(StudioDraftMetaStore metaStore, StudioAuditService auditService) {
        this.metaStore = metaStore;
        this.auditService = auditService;
    }

    public void onDraftSaved(String assetType, String assetId, String origin) {
        metaStore.markDraft(assetType, assetId, origin);
        auditService.draftSaved(assetType, assetId, origin);
    }

    public void onDraftDeleted(String assetType, String assetId) {
        metaStore.delete(assetType, assetId);
        auditService.draftRollback(assetType, assetId);
    }
}
