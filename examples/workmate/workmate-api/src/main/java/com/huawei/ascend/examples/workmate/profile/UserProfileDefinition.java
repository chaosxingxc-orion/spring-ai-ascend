package com.huawei.ascend.examples.workmate.profile;

import java.util.List;

public record UserProfileDefinition(String role, List<String> interests) {

    public UserProfileDefinition {
        role = role == null ? "" : role.trim();
        interests = interests == null ? List.of() : List.copyOf(interests);
    }

    public boolean isEmpty() {
        return role.isBlank() && interests.isEmpty();
    }
}
