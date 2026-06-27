package com.huawei.ascend.examples.workmate.skill;

import java.util.List;

public record SkillScanResult(String skillId, boolean safe, List<String> warnings) {

    public SkillScanResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
