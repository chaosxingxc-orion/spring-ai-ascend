package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A failing sink must be isolated — never starving the other sinks or breaking the drain. */
class CompositeTrajectorySinkTest {

    private static TrajectoryEvent event(long seq) {
        return new TrajectoryEvent(seq, Kind.RUN_START, 0L, null, "task", "span", null, "tenant", "ctx", "task",
                "run", null, null, null, null, null, null, null, null, "2");
    }

    private static final class RecordingSink implements TrajectorySink {
        final List<TrajectoryEvent> events = new ArrayList<>();
        boolean opened;
        boolean closed;

        @Override public void onOpen(String contextId, String taskId) { opened = true; }

        @Override public void accept(TrajectoryEvent event) { events.add(event); }

        @Override public void onClose() { closed = true; }
    }

    private static final class ThrowingSink implements TrajectorySink {
        @Override public void onOpen(String contextId, String taskId) { throw new IllegalStateException("open"); }

        @Override public void accept(TrajectoryEvent event) { throw new IllegalStateException("accept"); }

        @Override public void onClose() { throw new IllegalStateException("close"); }
    }

    @Test
    void oneThrowingSinkDoesNotStarveOthers() {
        RecordingSink good = new RecordingSink();
        CompositeTrajectorySink composite = new CompositeTrajectorySink(List.of(new ThrowingSink(), good));

        composite.onOpen("ctx", "task");
        composite.accept(event(0));
        composite.accept(event(1));
        composite.onClose();

        assertThat(good.opened).isTrue();
        assertThat(good.closed).isTrue();
        assertThat(good.events).extracting(TrajectoryEvent::seq).containsExactly(0L, 1L);
    }

    /**
     * A sink failure is exactly the moment the log must say whose run was affected; emission
     * may run on a framework worker thread, so the WARN inlines the correlation keys handed
     * over at onOpen instead of relying on the executor's thread-local MDC.
     */
    @Test
    void sinkFailureWarnInlinesCorrelationKeysWithoutMdc() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(CompositeTrajectorySink.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CompositeTrajectorySink composite = new CompositeTrajectorySink(List.of(new ThrowingSink()));
            composite.onOpen("ctx", "task");
            composite.accept(event(0));
            composite.onClose();

            assertThat(appender.list).isNotEmpty().allSatisfy(event ->
                    assertThat(event.getFormattedMessage())
                            .contains("contextId=ctx")
                            .contains("taskId=task"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
