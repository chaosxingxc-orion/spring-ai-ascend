package com.huawei.ascend.runtime.engine.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentScopeStreamAdapterTest {

    private final AgentScopeStreamAdapter adapter = new AgentScopeStreamAdapter();

    @Test
    void mapsSseEventMapsWithoutExtendingRuntimeEventTypes() {
        List<AgentExecutionResult> results = adapter.adapt(Stream.of(
                Map.of("object", "content", "type", "text", "text", "hello"),
                Map.of("status", "completed", "output", "done"),
                Map.of("status", "error", "error_code", "BAD", "message", "boom"),
                Map.of("status", "input_required", "text", "city?"))).toList();

        assertThat(results).extracting(AgentExecutionResult::type).containsExactly(
                AgentExecutionResult.Type.OUTPUT,
                AgentExecutionResult.Type.COMPLETED,
                AgentExecutionResult.Type.FAILED,
                AgentExecutionResult.Type.INTERRUPTED);
        assertThat(results.get(0).output().getContent()).isEqualTo("hello");
        assertThat(results.get(1).output().getContent()).isEqualTo("done");
        assertThat(results.get(2).errorCode()).isEqualTo("BAD");
        assertThat(results.get(3).prompt()).isEqualTo("city?");
    }

    @Test
    void mapsFailedStatusMapToFailedResult() {
        AgentExecutionResult result = adapter.map(Map.of("status", "failed", "message", "boom"));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(result.errorMessage()).isEqualTo("boom");
    }

    @Test
    void mapsFailureEventMapToFailedResult() {
        AgentExecutionResult result = adapter.map(Map.of("event", "failure", "error_code", "BAD", "message", "boom"));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(result.errorCode()).isEqualTo("BAD");
        assertThat(result.errorMessage()).isEqualTo("boom");
    }

    @Test
    void mapsInProgressEventWithNullErrorToOutputResult() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("status", "in_progress");
        event.put("error", null);
        event.put("text", "hi");

        AgentExecutionResult result = adapter.map(event);

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.OUTPUT);
        assertThat(result.output().getContent()).isEqualTo("hi");
    }

    @Test
    void doesNotTreatStatusSubstringsAsTerminalResults() {
        for (String status : List.of(
                "no_error",
                "error_cleared",
                "failover_ok",
                "no_failures",
                "exception_handled",
                "semifinal",
                "finalizing")) {
            AgentExecutionResult result = adapter.map(Map.of("status", status, "text", "hi"));

            assertThat(result.type()).as(status).isEqualTo(AgentExecutionResult.Type.OUTPUT);
            assertThat(result.output().getContent()).as(status).isEqualTo("hi");
        }
    }
}
