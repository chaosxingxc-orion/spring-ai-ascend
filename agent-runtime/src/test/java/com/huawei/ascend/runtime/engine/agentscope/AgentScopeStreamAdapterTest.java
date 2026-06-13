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
        assertThat(results.get(0).outputContent()).isEqualTo("hello");
        assertThat(results.get(1).outputContent()).isEqualTo("done");
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
        assertThat(result.outputContent()).isEqualTo("hi");
    }

    @Test
    void mapsRuntimeMessageEventWithNestedContentToOutput() {
        AgentExecutionResult result = adapter.map(Map.of(
                "object", "message",
                "type", "message",
                "status", "completed",
                "role", "assistant",
                "content", List.of(Map.of("object", "content", "type", "text", "text", "hello"))));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.OUTPUT);
        assertThat(result.outputContent()).isEqualTo("hello");
    }

    @Test
    void mapsRuntimeResponseEventWithNestedOutputToCompleted() {
        AgentExecutionResult result = adapter.map(Map.of(
                "object", "response",
                "status", "completed",
                "output", List.of(Map.of(
                        "role", "assistant",
                        "content", List.of(Map.of("type", "text", "text", "hello"))))));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.COMPLETED);
        assertThat(result.outputContent()).isEqualTo("hello");
    }

    @Test
    void treatsBooleanFalseErrorFieldAsAbsent() {
        AgentExecutionResult result = adapter.map(Map.of("status", "in_progress", "error", false, "text", "hi"));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.OUTPUT);
        assertThat(result.outputContent()).isEqualTo("hi");
    }

    @Test
    void extractsCodeAndMessageFromNestedErrorObject() {
        AgentExecutionResult result = adapter.map(Map.of(
                "status", "failed",
                "error", Map.of("code", "E_UPSTREAM", "message", "boom")));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(result.errorCode()).isEqualTo("E_UPSTREAM");
        assertThat(result.errorMessage()).isEqualTo("boom");
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
            assertThat(result.outputContent()).as(status).isEqualTo("hi");
        }
    }

    // --- AgentScopeStatus.fromWire coverage ---

    @Test
    void fromWireClassifiesAllFailureStrings() {
        for (String s : List.of("error", "errored", "failed", "failure", "exception")) {
            assertThat(AgentScopeStatus.fromWire(s)).as(s).isEqualTo(AgentScopeStatus.FAILURE);
        }
    }

    @Test
    void fromWireClassifiesAllInterruptStrings() {
        for (String s : List.of(
                "interrupt", "interrupted", "input_required", "requires_input", "human", "human_input")) {
            assertThat(AgentScopeStatus.fromWire(s)).as(s).isEqualTo(AgentScopeStatus.INTERRUPT);
        }
    }

    @Test
    void fromWireClassifiesAllCompletedStrings() {
        for (String s : List.of("completed", "complete", "final", "finished", "done", "success", "succeeded")) {
            assertThat(AgentScopeStatus.fromWire(s)).as(s).isEqualTo(AgentScopeStatus.COMPLETED);
        }
    }

    @Test
    void fromWireIsCaseAndWhitespaceInsensitive() {
        assertThat(AgentScopeStatus.fromWire("  FAILED  ")).isEqualTo(AgentScopeStatus.FAILURE);
        assertThat(AgentScopeStatus.fromWire("Completed")).isEqualTo(AgentScopeStatus.COMPLETED);
        assertThat(AgentScopeStatus.fromWire("INTERRUPT")).isEqualTo(AgentScopeStatus.INTERRUPT);
    }

    @Test
    void fromWireReturnsUnknownForUnrecognizedNonBlankStatus() {
        assertThat(AgentScopeStatus.fromWire("weird_new_state")).isEqualTo(AgentScopeStatus.UNKNOWN);
        assertThat(AgentScopeStatus.fromWire("pending")).isEqualTo(AgentScopeStatus.UNKNOWN);
        assertThat(AgentScopeStatus.fromWire("in_progress")).isEqualTo(AgentScopeStatus.UNKNOWN);
    }

    @Test
    void fromWireReturnsUnknownForNullOrBlank() {
        assertThat(AgentScopeStatus.fromWire(null)).isEqualTo(AgentScopeStatus.UNKNOWN);
        assertThat(AgentScopeStatus.fromWire("")).isEqualTo(AgentScopeStatus.UNKNOWN);
        assertThat(AgentScopeStatus.fromWire("   ")).isEqualTo(AgentScopeStatus.UNKNOWN);
    }

    @Test
    void unknownStatusClassifiesToOutputSafeDefault() {
        // An unrecognized status must fall through to OUTPUT — the safe default —
        // so no signal is lost and the call is still surfaced to the caller.
        AgentExecutionResult result = adapter.map(Map.of("status", "weird_new_state", "text", "payload"));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.OUTPUT);
        assertThat(result.outputContent()).isEqualTo("payload");
    }
}
