package com.huawei.ascend.examples.workmate.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SessionSkillIdsTest {

    @Test
    void deduplicatesAfterNormalization() {
        assertThat(SessionSkillIds.normalize(List.of(" skill-a ", "skill-a", "skill-b")))
                .containsExactly("skill-a", "skill-b");
    }
}
