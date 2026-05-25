package com.huawei.ascend.service.engine.spi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentInvokeRequestTest {

    @Test
    void constructorRejectsNullRequiredFields() {
        assertThatThrownBy(() -> new AgentInvokeRequest(
                null,
                "task",
                "session",
                "tenant",
                Map.of(),
                List.of(),
                Map.of(),
                "trace"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("runId");
    }

    @Test
    void collectionFieldsAreDefensivelyCopied() {
        Map<String, Object> sessionContext = new HashMap<>();
        sessionContext.put("projection_policy", "last_n");
        List<String> injectedSkills = new ArrayList<>();
        injectedSkills.add("search");
        Map<String, Object> taskMetadata = new HashMap<>();
        taskMetadata.put("step_number", 1);

        AgentInvokeRequest request = new AgentInvokeRequest(
                "run",
                "task",
                "session",
                "tenant",
                sessionContext,
                injectedSkills,
                taskMetadata,
                "trace");

        sessionContext.put("projection_policy", "summary_v1");
        injectedSkills.add("shell");
        taskMetadata.put("step_number", 2);

        assertThat(request.sessionContext()).containsEntry("projection_policy", "last_n");
        assertThat(request.injectedSkills()).containsExactly("search");
        assertThat(request.taskMetadata()).containsEntry("step_number", 1);
        assertThatThrownBy(() -> request.sessionContext().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.injectedSkills().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.taskMetadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
