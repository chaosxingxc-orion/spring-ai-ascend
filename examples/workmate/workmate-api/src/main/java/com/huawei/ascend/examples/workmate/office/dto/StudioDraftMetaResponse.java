package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.StudioDraftMeta;

public record StudioDraftMetaResponse(
        String assetType, String assetId, String status, String origin, String updatedAt) {

    public static StudioDraftMetaResponse from(StudioDraftMeta meta) {
        return new StudioDraftMetaResponse(
                meta.assetType(), meta.assetId(), meta.status(), meta.origin(), meta.updatedAt());
    }
}
