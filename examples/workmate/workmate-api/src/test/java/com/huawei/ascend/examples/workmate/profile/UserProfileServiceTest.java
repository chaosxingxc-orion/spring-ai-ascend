package com.huawei.ascend.examples.workmate.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserProfileServiceTest {

    @TempDir
    Path dataDir;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        UserProfileStore store =
                new UserProfileStore(new WorkmateDataProperties(dataDir.toString()), new ObjectMapper());
        service = new UserProfileService(store);
    }

    @Test
    void savesAndInjectsProfile() {
        service.updateProfile(new UserProfileDefinition("产品经理", List.of("research", "writing")));

        UserProfileDefinition saved = service.getProfile();
        assertThat(saved.role()).isEqualTo("产品经理");
        assertThat(saved.interests()).containsExactly("research", "writing");
        assertThat(service.loadForInjection())
                .contains("Role: 产品经理")
                .contains("Interests: research, writing");
    }
}
