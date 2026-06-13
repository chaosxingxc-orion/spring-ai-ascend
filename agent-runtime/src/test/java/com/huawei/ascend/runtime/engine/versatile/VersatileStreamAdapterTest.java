package com.huawei.ascend.runtime.engine.versatile;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Verifies SSE line → AgentExecutionResult mapping for standard and
 * custom event types.
 */
class VersatileStreamAdapterTest {

    private final VersatileStreamAdapter adapter = new VersatileStreamAdapter();

    @Test
    void mapsMessageEventToOutput() {
        List<AgentExecutionResult> results = adapt(
                "data:{\"event\":\"message\",\"data\":{\"text\":\"hello\"}}");

        assertThat(results).hasSize(1);
        AgentExecutionResult r = results.get(0);
        assertThat(r.type()).isEqualTo(AgentExecutionResult.Type.OUTPUT);
        assertThat(r.outputContent()).isEqualTo("hello");
    }

    @Test
    void mapsMessageSummaryWhenFinished() {
        List<AgentExecutionResult> results = adapt(
                "data:{\"event\":\"message\",\"data\":{\"text\":\"\",\"summary\":\"done\",\"is_finished\":true}}");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).outputContent()).isEqualTo("done");
    }

    @Test
    void mapsWorkflowFinishedToCompleted() {
        List<AgentExecutionResult> results = adapt(
                "data:{\"event\":\"workflow_finished\",\"data\":{\"outputs\":{\"responseContent\":\"result\"}}}");

        assertThat(results).hasSize(1);
        AgentExecutionResult r = results.get(0);
        assertThat(r.type()).isEqualTo(AgentExecutionResult.Type.COMPLETED);
        assertThat(r.outputContent()).isEqualTo("result");
    }

    @Test
    void mapsWorkflowFinishedNoOutputsToCompletedEmpty() {
        List<AgentExecutionResult> results = adapt(
                "data:{\"event\":\"workflow_finished\",\"data\":{}}");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEmpty();
    }

    @Test
    void mapsEndEventToInterruptedWhenNoEndNodeType() {
        // Bare "end" without a preceding message(node_type=End) → INTERRUPTED
        List<AgentExecutionResult> results = adapt(
                "data:{\"event\":\"end\",\"data\":{}}");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.INTERRUPTED);
    }

    @Test
    void mapsEndEventAfterEndNodeTypeToCompleted() {
        // message(node_type=End) before "end" → COMPLETED
        List<AgentExecutionResult> results = adapt(
                "data:{\"event\":\"message\",\"data\":{\"node_type\":\"End\",\"text\":\"OK\"}}",
                "data:{\"event\":\"end\",\"data\":{}}");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.OUTPUT);
        assertThat(results.get(1).type()).isEqualTo(AgentExecutionResult.Type.COMPLETED);
    }

    @Test
    void mapsExceptionToFailed() {
        List<AgentExecutionResult> results = adapt(
                "data:{\"event\":\"exception\",\"data\":{\"code\":\"ERR-1001\",\"message\":\"db failed\"}}");

        assertThat(results).hasSize(1);
        AgentExecutionResult r = results.get(0);
        assertThat(r.type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(r.errorCode()).isEqualTo("VERSATILE_ERR-1001");
        assertThat(r.errorMessage()).isEqualTo("db failed");
    }

    @Test
    void filtersControlEvents() {
        List<AgentExecutionResult> results = adapt(
                "data:{\"event\":\"workflow_started\",\"data\":{}}",
                "data:{\"event\":\"node_started\",\"data\":{\"node_name\":\"开始\"}}",
                "data:{\"event\":\"node_finished\",\"data\":{\"node_name\":\"开始\"}}");

        assertThat(results).isEmpty();
    }

    @Test
    void mapsCustomEventToOutput() {
        // Unknown events → raw passthrough (same as curl output)
        String sse = "data:{\"event\":\"hotels_info\",\"data\":{\"hotels\":[{\"name\":\"瑞吉\"}],\"index\":\"0\"}}";
        List<AgentExecutionResult> results = adapt(sse);

        assertThat(results).hasSize(1);
        AgentExecutionResult r = results.get(0);
        assertThat(r.type()).isEqualTo(AgentExecutionResult.Type.OUTPUT);
        // Raw line passthrough — preserves original SSE format
        assertThat(r.outputContent()).contains("hotels_info");
        assertThat(r.outputContent()).contains("瑞吉");
    }

    @Test
    void filtersCustomEventWithEmptyData() {
        List<AgentExecutionResult> results = adapt(
                "data:{\"event\":\"custom_empty\",\"data\":{}}");

        assertThat(results).isEmpty();
    }

    @Test
    void stripsDataPrefixAndHandlesRawSse() {
        List<AgentExecutionResult> results = adapt(
                "data:{\"event\":\"message\",\"data\":{\"node_type\":\"End\"}}",
                "data:{\"event\":\"end\",\"data\":{}}");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.COMPLETED);
    }

    @Test
    void skipsUnparseableLines() {
        List<AgentExecutionResult> results = adapt(
                "not-json-at-all",
                "data:{\"event\":\"message\",\"data\":{\"node_type\":\"End\"}}",
                "data:{\"event\":\"end\",\"data\":{}}");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.COMPLETED);
    }

    // ── Helpers ──

    private List<AgentExecutionResult> adapt(String... lines) {
        return adapter.adapt(Stream.of((Object[]) lines)).toList();
    }
}
