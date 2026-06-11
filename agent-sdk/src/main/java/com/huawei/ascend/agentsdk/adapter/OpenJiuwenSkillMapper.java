package com.huawei.ascend.agentsdk.adapter;

import com.huawei.ascend.agentsdk.spec.skill.SkillSpec;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class OpenJiuwenSkillMapper {
    public List<String> toSkillDirectories(List<SkillSpec> skills) {
        return skills.stream()
                .map(skill -> skill.path().toString())
                .toList();
    }

    public List<String> toSkillRootDirectories(List<SkillSpec> skills) {
        Set<Path> roots = new LinkedHashSet<>();
        for (SkillSpec skill : skills) {
            Path parent = skill.path().getParent();
            roots.add(parent == null ? skill.path() : parent);
        }
        return roots.stream()
                .map(Path::toString)
                .toList();
    }
}

