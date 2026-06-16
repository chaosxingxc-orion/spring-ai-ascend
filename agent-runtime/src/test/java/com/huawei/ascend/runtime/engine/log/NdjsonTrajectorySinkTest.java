package com.huawei.ascend.runtime.engine.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.ErrorCategory;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.ErrorInfo;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Usage;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The NDJSON rail must emit exactly one parseable JSON object per event with the canonical
 * field set (identical to the A2A rail, minus the never-serialized attempt/retryable), omit
 * nulls, and never let a serialization fault break the run or leak onto the stream.
 */
class NdjsonTrajectorySinkTest {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final NdjsonTrajectorySink sink = new NdjsonTrajectorySink(NdjsonTrajectorySink.defaultMapper());
    private final ObjectMapper parser = new ObjectMapper();
    private Logger trajectoryLogger;

    @BeforeEach
    void attach() {
        trajectoryLogger = (Logger) LoggerFactory.getLogger(NdjsonTrajectorySink.TRAJECTORY_LOGGER);
        trajectoryLogger.setLevel(Level.INFO);
        appender.start();
        trajectoryLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        trajectoryLogger.detachAppender(appender);
    }

    private Map<String, Object> parsed(int index) throws Exception {
        return parser.readValue(appender.list.get(index).getFormattedMessage(), JSON_OBJECT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> json, String key) {
        return (Map<String, Object>) json.get(key);
    }

    @Test
    void writesOneJsonObjectPerEventWithTheCanonicalFieldSet() throws Exception {
        sink.accept(new TrajectoryEvent(7L, Kind.MODEL_CALL_END, 1700L, 42L, "trace-1", "span-1", "parent-1",
                "tenant-1", "ctx-1", "task-1", "model_call", "gpt", Map.of("q", "hi"), "answer",
                new Usage(10, 20, 5.0, "gpt", "openai", 1500L), 1, true,
                new ErrorInfo("OJ", "boom", ErrorCategory.TIMEOUT), "thinking", "stop", "ptask", "ptask", "3"));

        assertThat(appender.list).hasSize(1);
        Map<String, Object> json = parsed(0);
        assertThat(json)
                .containsEntry("seq", 7).containsEntry("kind", "MODEL_CALL_END")
                .containsEntry("tsEpochMillis", 1700).containsEntry("durationMs", 42)
                .containsEntry("traceId", "trace-1").containsEntry("spanId", "span-1")
                .containsEntry("parentSpanId", "parent-1").containsEntry("tenantId", "tenant-1")
                .containsEntry("contextId", "ctx-1").containsEntry("taskId", "task-1")
                .containsEntry("object", "model_call").containsEntry("name", "gpt")
                .containsEntry("result", "answer").containsEntry("reasoning", "thinking")
                .containsEntry("finishReason", "stop").containsEntry("parentTaskId", "ptask")
                .containsEntry("parentTraceId", "ptask").containsEntry("schemaVersion", "3");
        assertThat(nested(json, "usage")).containsEntry("provider", "openai").containsEntry("costMicros", 1500);
        assertThat(nested(json, "error")).containsEntry("category", "TIMEOUT");
        // attempt/retryable stay on the Java record but are never serialized northbound.
        assertThat(json).doesNotContainKey("attempt").doesNotContainKey("retryable");
    }

    @Test
    void omitsNullFields() throws Exception {
        sink.accept(new TrajectoryEvent(0L, Kind.RUN_START, 1000L, null, "t", "s", null, "tenant", "ctx", "task",
                "run", null, null, null, null, null, null, null, null, null, null, null, "3"));

        Map<String, Object> json = parsed(0);
        assertThat(json).containsKeys("seq", "kind", "tsEpochMillis", "traceId", "spanId", "tenantId",
                "contextId", "taskId", "object", "schemaVersion");
        assertThat(json).doesNotContainKeys("durationMs", "parentSpanId", "name", "args", "result",
                "usage", "error", "reasoning", "finishReason", "parentTaskId", "parentTraceId");
    }

    @Test
    void serializationFailureIsSwallowedAndNeverReachesTheStream() {
        TrajectoryEvent bad = new TrajectoryEvent(1L, Kind.TOOL_CALL_START, 1L, null, "t", "s", null, "tenant",
                "ctx", "task", "tool_call", "x", new Exploding(), null, null, null, null, null, null, null,
                null, null, "3");
        assertThatCode(() -> sink.accept(bad)).doesNotThrowAnyException();
        assertThat(appender.list).isEmpty();
    }

    /** A bean whose getter throws, to force a Jackson serialization fault on the args payload. */
    static class Exploding {
        public String getBoom() {
            throw new IllegalStateException("nope");
        }
    }
}
