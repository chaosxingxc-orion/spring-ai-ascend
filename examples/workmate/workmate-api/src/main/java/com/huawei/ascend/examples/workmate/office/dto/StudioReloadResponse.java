package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.OfficeReloadResult;
import java.util.List;

public record StudioReloadResponse(int experts, int skills, List<String> warnings) {

    public static StudioReloadResponse from(OfficeReloadResult result) {
        return new StudioReloadResponse(result.experts(), result.skills(), result.warnings());
    }
}
