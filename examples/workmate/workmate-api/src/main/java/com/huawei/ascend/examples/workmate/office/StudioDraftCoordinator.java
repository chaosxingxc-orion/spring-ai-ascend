package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.ImportValidationResponse;
import org.springframework.stereotype.Component;

/**
 * Shared draft-save/delete lifecycle for Studio write services: audit + meta + registry reload.
 */
@Component
public class StudioDraftCoordinator {

    private final StudioDraftLifecycle draftLifecycle;

    public StudioDraftCoordinator(StudioDraftLifecycle draftLifecycle) {
        this.draftLifecycle = draftLifecycle;
    }

    public void commitSaved(String assetType, String assetId, String origin, Runnable reloadRegistry) {
        draftLifecycle.onDraftSaved(assetType, assetId, origin);
        reloadRegistry.run();
    }

    public void commitDeleted(String assetType, String assetId, Runnable reloadRegistry) {
        draftLifecycle.onDraftDeleted(assetType, assetId);
        reloadRegistry.run();
    }

    public void rollbackDraft(
            String assetType,
            String assetId,
            DraftExistenceCheck existenceCheck,
            Runnable deleteDraft,
            Runnable reloadRegistry,
            String notFoundMessage) {
        if (!existenceCheck.exists(assetId)) {
            throw new IllegalArgumentException(notFoundMessage);
        }
        deleteDraft.run();
        commitDeleted(assetType, assetId, reloadRegistry);
    }

    public static String resolveOrigin(boolean baselineExists) {
        return baselineExists ? "override" : "blank";
    }

    public static ImportValidationResponse validate(Runnable normalizer) {
        try {
            normalizer.run();
            return new ImportValidationResponse(true, "OK");
        } catch (IllegalArgumentException ex) {
            return new ImportValidationResponse(false, ex.getMessage());
        }
    }

    @FunctionalInterface
    public interface DraftExistenceCheck {
        boolean exists(String assetId);
    }
}
