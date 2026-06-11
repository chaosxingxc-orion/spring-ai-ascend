package com.huawei.ascend.agentsdk.spec.skill;

import java.nio.file.Path;

public record SkillSourceSpec(
        String type,
        Path path) {
}

