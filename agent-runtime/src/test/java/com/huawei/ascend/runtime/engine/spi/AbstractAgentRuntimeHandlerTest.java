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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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

    /** Only supports the mandatory core; a model call must be dropped even at FULL. */
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

    private static List<TrajectoryEvent> run(AbstractAgentRuntimeHandler handler, TrajectoryLevel level)
            throws InterruptedException {
        AgentExecutionContext context = new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "sess", "task1", "agent"),
                "USER_MESSAGE", List.of(), Map.of());
        TrajectorySettings settings = level == TrajectoryLevel.OFF
                ? TrajectorySettings.off()
                : new TrajectorySettings(level, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        TrajectoryChannel channel = handler.openTrajectory(context, settings);
        List<TrajectoryEvent> events = new CopyOnWriteArrayList<>();
        Thread drainer = new Thread(() -> channel.drain().forEach(events::add));
        drainer.start();
        try (Stream<?> raw = handler.execute(context)) {
            raw.forEach(x -> { });
        }
        drainer.join(3000);
        return new ArrayList<>(events);
    }

    @Test
    @Timeout(10)
    void fullLevelEmitsLifecycleWithMonotonicSeqAndCorrelation() throws Exception {
        List<TrajectoryEvent> events = run(new FullHandler(), TrajectoryLevel.FULL);
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
    @Timeout(10)
    void fullLevelMasksSensitiveArgs() throws Exception {
        List<TrajectoryEvent> events = run(new FullHandler(), TrajectoryLevel.FULL);
        TrajectoryEvent toolStart = events.stream()
                .filter(e -> e.kind() == Kind.TOOL_CALL_START).findFirst().orElseThrow();
        assertThat(toolStart.args()).isInstanceOf(Map.class);
        Map<?, ?> args = (Map<?, ?>) toolStart.args();
        assertThat(args.get("apiKey")).isEqualTo("***");
        assertThat(args.get("q")).isEqualTo("hi");
    }

    @Test
    @Timeout(10)
    void summaryLevelDropsOptionalTierModelCall() throws Exception {
        List<TrajectoryEvent> events = run(new FullHandler(), TrajectoryLevel.SUMMARY);
        assertThat(events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.TOOL_CALL_START, Kind.RUN_END);
    }

    @Test
    @Timeout(10)
    void offLevelEmitsNothing() throws Exception {
        assertThat(run(new FullHandler(), TrajectoryLevel.OFF)).isEmpty();
    }

    @Test
    @Timeout(10)
    void unsupportedKindIsDroppedEvenAtFull() throws Exception {
        List<TrajectoryEvent> events = run(new CoreOnlyHandler(), TrajectoryLevel.FULL);
        assertThat(events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.TOOL_CALL_START, Kind.RUN_END);
    }
}
