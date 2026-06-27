package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.OfficeAssetSource;

public record StudioWelcomeSourceResponse(
        String welcomeYaml,
        String baselineYaml,
        OfficeAssetSource source,
        String sourcePath) {}
