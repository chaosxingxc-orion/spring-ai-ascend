package com.huawei.ascend.examples.workmate.office.dto;

import java.util.List;

public record StudioExportPreviewResponse(List<StudioExportItemResponse> items, int expertCount, int skillCount) {}
