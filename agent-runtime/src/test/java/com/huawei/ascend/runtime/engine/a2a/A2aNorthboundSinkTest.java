package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

/**
 * The northbound buffer is bounded and best-effort, but shedding must never be silent
 * toward an auditing caller: the flushed artifact carries an explicit truncation marker,
 * the terminal kinds survive the overflow, and the operator WARN is attributable to a
 * task without relying on MDC (emission may run on a framework worker thread).
 */
class A2aNorthboundSinkTest {

    private static final int CAPACITY = 10_000;
    private static final int TERMINAL_RESERVE = 16;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(A2aNorthboundSink.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void overflow_appendsTruncatedMarkerWithDroppedCountAndWarnsWithTaskId() {
        A2aNorthboundSink sink = new A2aNorthboundSink();
        int offered = CAPACITY + 5;
        for (int i = 0; i < offered; i++) {
            sink.accept(event(i, Kind.TOOL_CALL_START));
        }

        AgentEmitter emitter = mock(AgentEmitter.class);
        sink.flush(emitter, "task-1-trajectory", false);

        ArgumentCaptor<List> partsCaptor = ArgumentCaptor.forClass(List.class);
        verify(emitter).addArtifact(partsCaptor.capture(), eq("task-1-trajectory"), eq("agent-trajectory"),
                any(), eq(false), eq(true));
        int accepted = CAPACITY - TERMINAL_RESERVE;
        List<?> parts = partsCaptor.getValue();
        assertThat(parts).hasSize(accepted + 1);
        Map<String, Object> marker = (Map<String, Object>) ((DataPart) parts.get(accepted)).data();
        assertThat(marker)
                .containsEntry("kind", "TRUNCATED")
                .containsEntry("droppedCount", offered - accepted);
        assertThat(appender.list)
                .as("the capacity WARN must be attributable to the task without MDC")
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("taskId=task-1"));
    }

    /** The reserved slots keep the run's outcome visible even when the buffer overflowed. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void terminalRunEndUsesTheReservedSlots_soACutTrajectoryStillCloses() {
        A2aNorthboundSink sink = new A2aNorthboundSink();
        for (int i = 0; i < CAPACITY; i++) {
            sink.accept(event(i, Kind.TOOL_CALL_START));
        }
        sink.accept(event(CAPACITY, Kind.RUN_END));

        AgentEmitter emitter = mock(AgentEmitter.class);
        sink.flush(emitter, "task-1-trajectory", false);

        ArgumentCaptor<List> partsCaptor = ArgumentCaptor.forClass(List.class);
        verify(emitter).addArtifact(partsCaptor.capture(), eq("task-1-trajectory"), eq("agent-trajectory"),
                any(), eq(false), eq(true));
        int accepted = CAPACITY - TERMINAL_RESERVE;
        List<?> parts = partsCaptor.getValue();
        assertThat(parts).hasSize(accepted + 2);
        assertThat((Map<String, Object>) ((DataPart) parts.get(accepted)).data())
                .containsEntry("kind", "RUN_END");
        assertThat((Map<String, Object>) ((DataPart) parts.get(accepted + 1)).data())
                .containsEntry("kind", "TRUNCATED")
                .containsEntry("droppedCount", TERMINAL_RESERVE);
    }

    /** No overflow means no marker — a complete trajectory must look complete. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void noOverflow_flushCarriesNoTruncationMarker() {
        A2aNorthboundSink sink = new A2aNorthboundSink();
        sink.accept(event(0, Kind.RUN_START));
        sink.accept(event(1, Kind.RUN_END));

        AgentEmitter emitter = mock(AgentEmitter.class);
        sink.flush(emitter, "task-1-trajectory", false);

        ArgumentCaptor<List> partsCaptor = ArgumentCaptor.forClass(List.class);
        verify(emitter).addArtifact(partsCaptor.capture(), eq("task-1-trajectory"), eq("agent-trajectory"),
                any(), eq(false), eq(true));
        List<?> parts = partsCaptor.getValue();
        assertThat(parts).hasSize(2);
        assertThat(parts).allSatisfy(p ->
                assertThat(((Map<String, Object>) ((DataPart) p).data()).get("kind")).isNotEqualTo("TRUNCATED"));
    }

    private static TrajectoryEvent event(long seq, Kind kind) {
        return new TrajectoryEvent(seq, kind, 0L, null, "task-1", "span", null, "tenant", "ctx-1", "task-1",
                "run", null, null, null, null, null, null, null, null, "2");
    }
}
