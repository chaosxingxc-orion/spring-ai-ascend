package com.huawei.ascend.examples.langgraph;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LangGraphStreamAdapterTest {

    private final LangGraphStreamAdapter adapter = new LangGraphStreamAdapter();

    /** values snapshots repeat the whole text — only the appended suffix may stream out. */
    @Test
    void valuesSnapshotsEmitOnlyTheAppendedDelta() {
        List<AgentExecutionResult> results = adapter.adapt(Stream.of(
                event("metadata", Map.of("run_id", "run-1")),
                event("values", Map.of("messages", List.of(
                        Map.of("type", "human", "content", "hi")))),
                event("values", Map.of("messages", List.of(
                        Map.of("type", "human", "content", "hi"),
                        Map.of("type", "ai", "content", "Hello")))),
                event("values", Map.of("messages", List.of(
                        Map.of("type", "human", "content", "hi"),
                        Map.of("type", "ai", "content", "Hello, world")))),
                event("end", null))).toList();

        assertThat(results).extracting(AgentExecutionResult::type).containsExactly(
                AgentExecutionResult.Type.OUTPUT,
                AgentExecutionResult.Type.OUTPUT,
                AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEqualTo("Hello");
        assertThat(results.get(1).outputContent()).isEqualTo(", world");
    }

    /**
     * With the checkpointer restoring conversation state, a follow-up turn's
     * first values snapshot echoes the prior turn's answer as the newest
     * assistant message — history behind the latest human message must not
     * replay as fresh OUTPUT.
     */
    @Test
    void followUpTurnDoesNotReplayThePriorAnswer() {
        List<AgentExecutionResult> results = adapter.adapt(Stream.of(
                event("values", Map.of("messages", List.of(
                        Map.of("type", "human", "content", "hi"),
                        Map.of("type", "ai", "content", "Hello, world"),
                        Map.of("type", "human", "content", "and again?")))),
                event("values", Map.of("messages", List.of(
                        Map.of("type", "human", "content", "hi"),
                        Map.of("type", "ai", "content", "Hello, world"),
                        Map.of("type", "human", "content", "and again?"),
                        Map.of("type", "ai", "content", "Hello again")))),
                event("end", null))).toList();

        assertThat(results).extracting(AgentExecutionResult::type).containsExactly(
                AgentExecutionResult.Type.OUTPUT,
                AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEqualTo("Hello again");
    }

    @Test
    void errorEventFailsWithCodeAndMessage() {
        List<AgentExecutionResult> results = adapter.adapt(Stream.of(
                event("error", Map.of("error", "ValueError", "message", "graph blew up")))).toList();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(results.get(0).errorCode()).isEqualTo("ValueError");
        assertThat(results.get(0).errorMessage()).isEqualTo("graph blew up");
    }

    /** messages/partial chunk lists carry cumulative content per message. */
    @Test
    void messagePartialChunksStreamDeltas() {
        List<AgentExecutionResult> results = adapter.adapt(Stream.of(
                event("messages/partial", List.of(Map.of("type", "AIMessageChunk", "content", "He"))),
                event("messages/partial", List.of(Map.of("type", "AIMessageChunk", "content", "Hello"))),
                event("end", null))).toList();

        assertThat(results).extracting(AgentExecutionResult::type).containsExactly(
                AgentExecutionResult.Type.OUTPUT,
                AgentExecutionResult.Type.OUTPUT,
                AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEqualTo("He");
        assertThat(results.get(1).outputContent()).isEqualTo("llo");
    }

    /** LangServe streams un-named data chunks with content parts and a terminal end event. */
    @Test
    void langServeDataEndDialectCompletes() {
        List<AgentExecutionResult> results = adapter.adapt(Stream.of(
                event("data", Map.of("content", List.of(Map.of("type", "text", "text", "advice")))),
                event("end", null))).toList();

        assertThat(results).extracting(AgentExecutionResult::type).containsExactly(
                AgentExecutionResult.Type.OUTPUT,
                AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEqualTo("advice");
    }

    /** A bare frame with an error field (no event name) must still fail the run. */
    @Test
    void bareErrorFrameFails() {
        List<AgentExecutionResult> results = adapter.adapt(Stream.of(
                (Object) Map.of("error", "boom", "message", "it broke"))).toList();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(results.get(0).errorMessage()).isEqualTo("it broke");
    }

    /** Two concurrent executions must not share delta state through the shared adapter. */
    @Test
    void concurrentAdaptCallsDoNotShareState() {
        List<AgentExecutionResult> first = adapter.adapt(Stream.of(
                event("values", Map.of("messages", List.of(Map.of("type", "ai", "content", "AAA")))))).toList();
        List<AgentExecutionResult> second = adapter.adapt(Stream.of(
                event("values", Map.of("messages", List.of(Map.of("type", "ai", "content", "AAA")))))).toList();

        assertThat(first).hasSize(1);
        assertThat(second).as("a fresh adapt() must re-emit the full text").hasSize(1);
        assertThat(second.get(0).outputContent()).isEqualTo("AAA");
    }

    private static Object event(String name, Object data) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put(LangGraphRuntimeClient.EVENT_KEY, name);
        event.put(LangGraphRuntimeClient.DATA_KEY, data);
        return event;
    }
}
