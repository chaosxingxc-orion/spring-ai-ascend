package com.huawei.ascend.examples.workmate.office;

import java.nio.file.Path;

public record PlaybookRegistryEntry(
        PlaybookDefinition definition, OfficeAssetSource source, Path sourcePath) {}
