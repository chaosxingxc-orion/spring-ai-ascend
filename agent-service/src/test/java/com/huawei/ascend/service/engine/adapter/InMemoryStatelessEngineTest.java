package com.huawei.ascend.service.engine.adapter;

import com.huawei.ascend.service.engine.spi.AgentInvokeRequest;
import com.huawei.ascend.service.engine.spi.StateDelta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryStatelessEngineTest {

    @Test
    void executeReturnsDeterministicNoOpDeltaWithoutMutatingRequest() {
        AgentInvokeRequest request = new AgentInvokeRequest(
                "run-1",
                "task-1",
                "session-1",
                "tenant-1",
                Map.of("messages", List.of(), "variables", Map.of()),
                List.of("echo"),
                Map.of("step_number", 1),
                "trace-1");

        StateDelta first = new InMemoryStatelessEngine().execute(request);
        StateDelta second = new InMemoryStatelessEngine().execute(request);

        assertThat(first).isEqualTo(second);
        assertThat(first.runStatusTransition()).isEqualTo(StateDelta.RunStatusTransition.NO_CHANGE);
        assertThat(first.taskStateDelta()).isEmpty();
        assertThat(first.sessionStateDelta()).isEmpty();
        assertThat(first.memoryWriteIntents()).isEmpty();
        assertThat(first.metrics()).containsEntry("engine", "InMemoryStatelessEngine")
                .containsEntry("request_id", "run-1");
    }
}
