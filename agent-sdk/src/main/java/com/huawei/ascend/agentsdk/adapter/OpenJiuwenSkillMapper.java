package com.huawei.ascend.agentsdk.adapter;

import com.huawei.ascend.agentsdk.spec.skill.SkillSpec;
import java.util.List;

public final class OpenJiuwenSkillMapper {
    public List<String> toSkillDirectories(List<SkillSpec> skills) {
        return skills.stream()
                .map(skill -> skill.path().toString())
                .toList();
    }
}

