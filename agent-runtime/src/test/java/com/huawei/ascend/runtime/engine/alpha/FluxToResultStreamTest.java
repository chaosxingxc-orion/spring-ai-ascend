package com.huawei.ascend.runtime.engine.alpha;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult.Type;
import com.openjiuwen.core.kernel.model.AgentEvent;
import com.openjiuwen.core.kernel.model.EventType;
import com.openjiuwen.core.kernel.model.TaskId;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bearing layer A: the deterministic event→result mapping. Every assertion is an
 * IFF token check — strip the mapping arm under test and the unique token vanishes,
 * turning the test red (mutation-RED). Timestamps are never asserted: {@link AgentEvent}
 * stamps {@code Instant.now()} internally and cannot be injected.
 */
class FluxToResultStreamTest {

    private static final TaskId TASK_ID = TaskId.generate();

    private static List<AgentExecutionResult> bridge(EventType type, String data) {
        return bridge(Flux.just(AgentEvent.of(TASK_ID, type, data)));
    }

    private static List<AgentExecutionResult> bridge(EventType type, String data, Map<String, String> metadata) {
        return bridge(Flux.just(AgentEvent.of(TASK_ID, type, data, metadata)));
    }

    private static List<AgentExecutionResult> bridge(Flux<AgentEvent> flux) {
        return new FluxToResultStream().apply(flux).toList();
    }

    private static List<Type> types(List<AgentExecutionResult> results) {
        return results.stream().map(AgentExecutionResult::type).toList();
    }

    @Test
    void completedMapsToCompletedResult() {
        List<AgentExecutionResult> r = bridge(EventType.TASK_COMPLETED, "FINAL_RESULT_TOKEN");
        assertThat(types(r)).containsExactly(Type.COMPLETED);
        assertThat(r.get(0).outputContent()).isEqualTo("FINAL_RESULT_TOKEN");
    }

    @Test
    void failedMapsToFailedResultWithMetadataCode() {
        List<AgentExecutionResult> r = bridge(EventType.TASK_FAILED, "boom", Map.of("errorCode", "E_CUSTOM"));
        assertThat(types(r)).containsExactly(Type.FAILED);
        assertThat(r.get(0).errorCode()).isEqualTo("E_CUSTOM");
        assertThat(r.get(0).errorMessage()).isEqualTo("boom");
    }

    @Test
    void failedFallsBackToDefaultCodeWhenMetadataAbsent() {
        List<AgentExecutionResult> r = bridge(EventType.TASK_FAILED, "boom");
        assertThat(r.get(0).errorCode()).isEqualTo(FluxToResultStream.DEFAULT_FAILURE_CODE);
        assertThat(r.get(0).errorMessage()).isEqualTo("boom");
    }

    @Test
    void pausedMapsToInterruptedResult() {
        List<AgentExecutionResult> r = bridge(EventType.TASK_PAUSED, "need approval");
        assertThat(types(r)).containsExactly(Type.INTERRUPTED);
        assertThat(r.get(0).prompt()).isEqualTo("need approval");
    }

    @Test
    void cancelledMapsToFailedResult() {
        List<AgentExecutionResult> r = bridge(EventType.TASK_CANCELLED, "aborted");
        assertThat(types(r)).containsExactly(Type.FAILED);
        assertThat(r.get(0).errorCode()).isEqualTo(EventType.TASK_CANCELLED.name());
        assertThat(r.get(0).errorMessage()).isEqualTo("aborted");
    }

    @Test
    void thinkingDeltaSurfacesAsOutputChunkBeforeTerminal() {
        List<AgentExecutionResult> r = bridge(Flux.just(
                AgentEvent.of(TASK_ID, EventType.THINKING_DELTA, "chunk"),
                AgentEvent.of(TASK_ID, EventType.TASK_COMPLETED, "done")));
        assertThat(types(r)).containsExactly(Type.OUTPUT, Type.COMPLETED);
        assertThat(r.get(0).outputContent()).isEqualTo("chunk");
        assertThat(r.get(1).outputContent()).isEqualTo("done");
    }

    @Test
    void thinkingBlockEventsSurfaceAsOutputChunks() {
        List<AgentExecutionResult> r = bridge(Flux.just(
                AgentEvent.of(TASK_ID, EventType.THINKING_BLOCK_START, "[start]"),
                AgentEvent.of(TASK_ID, EventType.THINKING_BLOCK_END, "[end]"),
                AgentEvent.of(TASK_ID, EventType.TASK_COMPLETED, "done")));
        assertThat(types(r)).containsExactly(Type.OUTPUT, Type.OUTPUT, Type.COMPLETED);
    }

    @Test
    void intermediateProcessEventsAreNotEmitted() {
        List<AgentExecutionResult> r = bridge(Flux.just(
                AgentEvent.of(TASK_ID, EventType.TASK_CREATED, "ignored-1"),
                AgentEvent.of(TASK_ID, EventType.TOOL_CALL, "ignored-2"),
                AgentEvent.of(TASK_ID, EventType.PLAN_GENERATED, "ignored-3"),
                AgentEvent.of(TASK_ID, EventType.THINKING_DELTA, "kept"),
                AgentEvent.of(TASK_ID, EventType.TASK_COMPLETED, "done")));
        assertThat(types(r)).containsExactly(Type.OUTPUT, Type.COMPLETED);
        assertThat(r.get(0).outputContent()).isEqualTo("kept");
    }

    @Test
    void streamWithoutTerminalDegradesToCompleted() {
        List<AgentExecutionResult> r = bridge(Flux.just(
                AgentEvent.of(TASK_ID, EventType.THINKING_DELTA, "x")));
        assertThat(types(r)).containsExactly(Type.OUTPUT, Type.COMPLETED);
        assertThat(r.get(r.size() - 1).type()).isEqualTo(Type.COMPLETED);
        assertThat(types(r)).doesNotContain(Type.FAILED, Type.INTERRUPTED);
    }

    @Test
    void firstTerminalWinsAndStopsDoubleTerminal() {
        List<AgentExecutionResult> r = bridge(Flux.just(
                AgentEvent.of(TASK_ID, EventType.TASK_COMPLETED, "first"),
                AgentEvent.of(TASK_ID, EventType.TASK_FAILED, "second", Map.of())));
        assertThat(types(r)).containsExactly(Type.COMPLETED);
        assertThat(r.get(0).outputContent()).isEqualTo("first");
    }

    @Test
    void everyEventTypeClassifiesIntoItsBucket() {
        Set<EventType> thinking = EnumSet.of(
                EventType.THINKING_DELTA, EventType.THINKING_BLOCK_START, EventType.THINKING_BLOCK_END);
        for (EventType type : EventType.values()) {
            List<AgentExecutionResult> r = bridge(type, "payload");
            List<Type> types = types(r);
            if (type == EventType.TASK_COMPLETED) {
                assertThat(types).as("%s -> COMPLETED", type).containsExactly(Type.COMPLETED);
                assertThat(r.get(0).outputContent()).as("%s outputContent", type).isEqualTo("payload");
            } else if (type == EventType.TASK_FAILED) {
                assertThat(types).as("%s -> FAILED", type).containsExactly(Type.FAILED);
            } else if (type == EventType.TASK_PAUSED) {
                assertThat(types).as("%s -> INTERRUPTED", type).containsExactly(Type.INTERRUPTED);
            } else if (type == EventType.TASK_CANCELLED) {
                assertThat(types).as("%s -> FAILED", type).containsExactly(Type.FAILED);
                assertThat(r.get(0).errorCode()).as("%s errorCode", type).isEqualTo(EventType.TASK_CANCELLED.name());
            } else if (thinking.contains(type)) {
                assertThat(types).as("%s -> OUTPUT then degraded COMPLETED", type)
                        .containsExactly(Type.OUTPUT, Type.COMPLETED);
            } else {
                assertThat(types).as("%s -> degraded COMPLETED only (process event dropped)", type)
                        .containsExactly(Type.COMPLETED);
            }
        }
    }
}
