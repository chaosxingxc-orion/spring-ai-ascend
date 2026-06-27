package com.huawei.ascend.examples.workmate.office.dto;

import java.util.List;

public record StudioExportItemResponse(
        String id,
        String assetType,
        String name,
        String suggestedOfficePath,
        List<String> files) {}
