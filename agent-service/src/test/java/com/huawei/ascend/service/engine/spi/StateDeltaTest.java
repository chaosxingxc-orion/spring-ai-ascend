package com.huawei.ascend.service.engine.spi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateDeltaTest {

    @Test
    void transitionIsAClosedTypedVocabulary() {
        assertThat(StateDelta.RunStatusTransition.NO_CHANGE.wireValue()).isEqualTo("no_change");
        assertThat(StateDelta.RunStatusTransition.values())
                .extracting(StateDelta.RunStatusTransition::wireValue)
                .containsExactlyInAnyOrder("no_change", "succeeded", "failed", "suspended", "yielded");
    }

    @Test
    void constructorRejectsNullRequiredFields() {
        assertThatThrownBy(() -> new StateDelta(
                null,
                Map.of(),
                Map.of(),
                List.of(),
                Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("runStatusTransition");
    }

    @Test
    void collectionFieldsAreDefensivelyCopiedIncludingMemoryIntentMaps() {
        Map<String, Object> taskState = new HashMap<>();
        taskState.put("step_number", 1);
        Map<String, Object> sessionState = new HashMap<>();
        sessionState.put("projection_policy", "last_n");
        Map<String, Object> memoryIntent = new HashMap<>();
        memoryIntent.put("op", "append");
        List<Map<String, Object>> memoryIntents = new ArrayList<>();
        memoryIntents.add(memoryIntent);
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("tokens", 1);

        StateDelta delta = new StateDelta(
                StateDelta.RunStatusTransition.NO_CHANGE,
                taskState,
                sessionState,
                memoryIntents,
                metrics);

        taskState.put("step_number", 2);
        sessionState.put("projection_policy", "summary_v1");
        memoryIntent.put("op", "replace");
        memoryIntents.add(Map.of("op", "delete"));
        metrics.put("tokens", 2);

        assertThat(delta.taskStateDelta()).containsEntry("step_number", 1);
        assertThat(delta.sessionStateDelta()).containsEntry("projection_policy", "last_n");
        assertThat(delta.memoryWriteIntents()).hasSize(1);
        assertThat(delta.memoryWriteIntents().get(0)).containsEntry("op", "append");
        assertThat(delta.metrics()).containsEntry("tokens", 1);
        assertThatThrownBy(() -> delta.memoryWriteIntents().get(0).put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
