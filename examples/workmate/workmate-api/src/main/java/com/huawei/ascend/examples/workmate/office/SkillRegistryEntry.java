package com.huawei.ascend.examples.workmate.office;

import java.nio.file.Path;

public record SkillRegistryEntry(
        SkillDefinition definition,
        OfficeAssetSource source,
        Path sourceDir,
        String skillFile) {}
