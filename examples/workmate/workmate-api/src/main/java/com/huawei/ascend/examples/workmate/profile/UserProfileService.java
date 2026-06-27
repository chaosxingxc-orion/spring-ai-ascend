package com.huawei.ascend.examples.workmate.profile;

import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final UserProfileStore store;

    public UserProfileService(UserProfileStore store) {
        this.store = store;
    }

    public UserProfileDefinition getProfile() {
        return store.get();
    }

    public UserProfileDefinition updateProfile(UserProfileDefinition profile) {
        return store.save(profile);
    }

    public String loadForInjection() {
        UserProfileDefinition profile = store.get();
        if (profile.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (!profile.role().isBlank()) {
            builder.append("- Role: ").append(profile.role()).append('\n');
        }
        if (!profile.interests().isEmpty()) {
            builder.append("- Interests: ").append(String.join(", ", profile.interests())).append('\n');
        }
        return builder.toString().trim();
    }
}
