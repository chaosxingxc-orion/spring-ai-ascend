package com.huawei.ascend.examples.workmate.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SessionSkillIds {

    private SessionSkillIds() {}

    public static String normalize(String skillId) {
        if (skillId == null) {
            return null;
        }
        return skillId.strip();
    }

    public static List<String> normalize(Collection<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String skillId : skillIds) {
            String canonical = normalize(skillId);
            if (canonical != null && !canonical.isBlank()) {
                normalized.add(canonical);
            }
        }
        return List.copyOf(new ArrayList<>(normalized));
    }
}
