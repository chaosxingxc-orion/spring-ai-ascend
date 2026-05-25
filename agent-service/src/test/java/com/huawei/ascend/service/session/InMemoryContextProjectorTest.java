package com.huawei.ascend.service.session;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryContextProjectorTest {

    @Test
    void projectCarriesTenantSessionAndDefaultPolicy() {
        Map<String, Object> projection = new InMemoryContextProjector()
                .project("session-1", "tenant-1", null);

        assertThat(projection).containsEntry("session_id", "session-1")
                .containsEntry("tenant_id", "tenant-1")
                .containsEntry("projection_policy", "last_n")
                .containsEntry("projection_window", 10);
        assertThat(projection).containsKeys("messages", "variables");
    }
}
