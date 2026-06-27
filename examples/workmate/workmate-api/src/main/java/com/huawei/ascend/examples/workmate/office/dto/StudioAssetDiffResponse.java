package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.OfficeAssetSource;
import java.util.List;

public record StudioAssetDiffResponse(
        boolean hasDraft,
        boolean hasBaseline,
        boolean canRollback,
        OfficeAssetSource currentSource,
        OfficeAssetSource baselineSource,
        String promptFile,
        String baselinePrompt,
        String draftPrompt,
        List<String> changedFields) {}
