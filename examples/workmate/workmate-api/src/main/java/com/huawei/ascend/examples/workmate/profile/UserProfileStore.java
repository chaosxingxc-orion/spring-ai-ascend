package com.huawei.ascend.examples.workmate.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.support.JsonFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class UserProfileStore {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileStore.class);
    private static final String FILE_NAME = "user-profile.json";

    private final JsonFileStore<UserProfileDefinition> backing;

    UserProfileStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.backing = new JsonFileStore<>(
                dataProperties.resolvedPath().resolve(FILE_NAME),
                objectMapper,
                UserProfileDefinition.class,
                () -> new UserProfileDefinition("", java.util.List.of()),
                LOG);
    }

    UserProfileDefinition get() {
        return backing.read(profile -> profile);
    }

    UserProfileDefinition save(UserProfileDefinition next) {
        UserProfileDefinition saved = next == null ? new UserProfileDefinition("", java.util.List.of()) : next;
        backing.replace(saved);
        return saved;
    }
}
