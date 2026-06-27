package com.huawei.ascend.examples.workmate.office;

public record StudioDraftMeta(String assetType, String assetId, String status, String origin, String updatedAt) {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
}
