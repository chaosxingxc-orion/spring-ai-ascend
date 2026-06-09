package com.huawei.ascend.agentsdk.spec.skill;

import java.nio.file.Path;

public record SkillSpec(
        String name,
        Path path,
        Path skillFile) {
}

