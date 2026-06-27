package com.huawei.ascend.examples.workmate.office;

import java.nio.file.Path;

public record ExpertRegistryEntry(
        ExpertDefinition definition,
        OfficeAssetSource source,
        Path sourceDir,
        String promptFile) {}
