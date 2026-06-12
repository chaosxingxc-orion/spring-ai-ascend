package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AbstractAgentRuntimeHandlerTest {

    /** Emits a tool call (with a sensitive arg) and a model call; supports every kind. */
    private static final class FullHandler extends AbstractAgentRuntimeHandler {
        FullHandler() { super("agent"); }

        @Override
        protected Set<Kind> supportedKinds() { return EnumSet.allOf(Kind.class); }

        @Override
        protected Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory) {
            trajectory.emit(TrajectoryDraft.toolCallStart("search", Map.of("q", "hi", "apiKey", "secret")));
            trajectory.emit(TrajectoryDraft.modelCallStart(Map.of("messages", 1)));
            return Stream.of("answer");
        }

        @Override
        public StreamAdapter resultAdapter() {
            return raw -> raw.map(o -> AgentExecutionResult.completed(String.valueOf(o)));
        }
    }

    /** Only supports the mandatory core; a model call must be dropped. */
    private static final class CoreOnlyHandler extends AbstractAgentRuntimeHandler {
        CoreOnlyHandler() { super("agent"); }

        @Override
        protected Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory) {
            trajectory.emit(TrajectoryDraft.modelCallStart(Map.of("messages", 1)));
            trajectory.emit(TrajectoryDraft.toolCallStart("search", "q"));
            return Stream.of("answer");
        }

        @Override
        public StreamAdapter resultAdapter() {
            return raw -> raw.map(o -> AgentExecutionResult.completed(String.valueOf(o)));
        }
    }

    /** Collects accepted events synchronously on the emitting thread. */
    private static final class CapturingSink implements TrajectorySink {
        final List<TrajectoryEvent> events = new ArrayList<>();

        @Override public void accept(TrajectoryEvent event) { events.add(event); }
    }

    private static List<TrajectoryEvent> run(AbstractAgentRuntimeHandler handler, boolean enabled) {
        AgentExecutionContext context = new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "sess", "task1", "agent"),
                "USER_MESSAGE", List.of(), Map.of());
        TrajectorySettings settings = enabled
                ? new TrajectorySettings(true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256)
                : TrajectorySettings.off();
        CapturingSink sink = new CapturingSink();
        handler.openTrajectory(context, settings, sink);
        try (Stream<?> raw = handler.execute(context)) {
            raw.forEach(x -> { });
        }
        return sink.events;
    }

    @Test
    void enabledEmitsLifecycleWithMonotonicSeqAndCorrelation() {
        List<TrajectoryEvent> events = run(new FullHandler(), true);
        assertThat(events).extracting(TrajectoryEvent::kind).containsExactly(
                Kind.RUN_START, Kind.TOOL_CALL_START, Kind.MODEL_CALL_START, Kind.RUN_END);
        assertThat(events).extracting(TrajectoryEvent::seq).containsExactly(0L, 1L, 2L, 3L);
        assertThat(events).allSatisfy(e -> {
            assertThat(e.taskId()).isEqualTo("task1");
            assertThat(e.contextId()).isEqualTo("sess");
            assertThat(e.schemaVersion()).isEqualTo(TrajectoryEvent.SCHEMA_VERSION);
        });
    }

    @Test
    void enabledMasksSensitiveArgs() {
        List<TrajectoryEvent> events = run(new FullHandler(), true);
        TrajectoryEvent toolStart = events.stream()
                .filter(e -> e.kind() == Kind.TOOL_CALL_START).findFirst().orElseThrow();
        assertThat(toolStart.args()).isInstanceOf(Map.class);
        Map<?, ?> args = (Map<?, ?>) toolStart.args();
        assertThat(args.get("apiKey")).isEqualTo("***");
        assertThat(args.get("q")).isEqualTo("hi");
    }

    @Test
    void disabledEmitsNothing() {
        assertThat(run(new FullHandler(), false)).isEmpty();
    }

    @Test
    void unsupportedKindIsDropped() {
        List<TrajectoryEvent> events = run(new CoreOnlyHandler(), true);
        assertThat(events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.TOOL_CALL_START, Kind.RUN_END);
    }
}
