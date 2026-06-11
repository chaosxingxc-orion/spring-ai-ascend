package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Span-tree derivation, timestamps, and level-filter balance of the stamper. */
class StampingTrajectoryEmitterTest {

    private static final RuntimeIdentity SCOPE = new RuntimeIdentity("tenant", "user", "sess", "task1", "agent");

    /** Collects publishes synchronously on the emitting thread. */
    private static final class CapturingChannel implements TrajectoryChannel {
        final List<TrajectoryEvent> events = new ArrayList<>();

        @Override public void publish(TrajectoryEvent event) { events.add(event); }

        @Override public Stream<TrajectoryEvent> drain() { return events.stream(); }

        @Override public void close() { }
    }

    private static StampingTrajectoryEmitter emitter(CapturingChannel channel, TrajectoryLevel level, Set<Kind> kinds) {
        TrajectorySettings settings =
                new TrajectorySettings(level, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        return new StampingTrajectoryEmitter(channel, SCOPE, settings, kinds);
    }

    private static TrajectoryEvent first(List<TrajectoryEvent> events, Kind kind) {
        return events.stream().filter(e -> e.kind() == kind).findFirst().orElseThrow();
    }

    @Test
    void spanPairsShareIdAndChainParents() {
        CapturingChannel channel = new CapturingChannel();
        StampingTrajectoryEmitter emitter = emitter(channel, TrajectoryLevel.FULL, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.toolCallStart("search", "q"));
        emitter.emit(TrajectoryDraft.toolCallEnd("search", "r"));
        emitter.emit(TrajectoryDraft.runEnd());

        TrajectoryEvent runStart = first(channel.events, Kind.RUN_START);
        TrajectoryEvent runEnd = first(channel.events, Kind.RUN_END);
        TrajectoryEvent toolStart = first(channel.events, Kind.TOOL_CALL_START);
        TrajectoryEvent toolEnd = first(channel.events, Kind.TOOL_CALL_END);

        // Root span has no parent; START/END of a span share one id.
        assertThat(runStart.parentSpanId()).isNull();
        assertThat(runEnd.parentSpanId()).isNull();
        assertThat(runEnd.spanId()).isEqualTo(runStart.spanId());
        assertThat(toolEnd.spanId()).isEqualTo(toolStart.spanId());
        // The tool span nests under the run span.
        assertThat(toolStart.parentSpanId()).isEqualTo(runStart.spanId());
        assertThat(toolEnd.parentSpanId()).isEqualTo(runStart.spanId());
        // traceId is the task id, tenantId the owning tenant, for every event.
        assertThat(channel.events).allSatisfy(e -> {
            assertThat(e.traceId()).isEqualTo("task1");
            assertThat(e.tenantId()).isEqualTo("tenant");
        });
    }

    @Test
    void endsCarryDurationStartsDoNot() {
        CapturingChannel channel = new CapturingChannel();
        StampingTrajectoryEmitter emitter = emitter(channel, TrajectoryLevel.FULL, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.runEnd());

        TrajectoryEvent runStart = first(channel.events, Kind.RUN_START);
        TrajectoryEvent runEnd = first(channel.events, Kind.RUN_END);
        assertThat(runStart.tsEpochMillis()).isPositive();
        assertThat(runEnd.tsEpochMillis()).isPositive();
        assertThat(runStart.durationMs()).isNull();
        assertThat(runEnd.durationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    void filteredOptionalTierKeepsParentChainBalanced() {
        CapturingChannel channel = new CapturingChannel();
        // SUMMARY drops MODEL_CALL_* even though the handler supports them.
        StampingTrajectoryEmitter emitter = emitter(channel, TrajectoryLevel.SUMMARY, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.modelCallStart("in"));      // dropped, but stack must stay balanced
        emitter.emit(TrajectoryDraft.toolCallStart("search", "q"));
        emitter.emit(TrajectoryDraft.toolCallEnd("search", "r"));
        emitter.emit(TrajectoryDraft.modelCallEnd(null, "stop", null)); // dropped
        emitter.emit(TrajectoryDraft.runEnd());

        assertThat(channel.events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.TOOL_CALL_START, Kind.TOOL_CALL_END, Kind.RUN_END);
        assertThat(channel.events).extracting(TrajectoryEvent::seq).containsExactly(0L, 1L, 2L, 3L);
        // The tool span parents to the RUN span, NOT the dropped (unpublished) model span.
        TrajectoryEvent runStart = first(channel.events, Kind.RUN_START);
        TrajectoryEvent toolStart = first(channel.events, Kind.TOOL_CALL_START);
        assertThat(toolStart.parentSpanId()).isEqualTo(runStart.spanId());
    }

    @Test
    void pointEventHangsOffOpenSpan() {
        CapturingChannel channel = new CapturingChannel();
        StampingTrajectoryEmitter emitter = emitter(channel, TrajectoryLevel.FULL, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.reasoning("thinking"));
        emitter.emit(TrajectoryDraft.runEnd());

        TrajectoryEvent runStart = first(channel.events, Kind.RUN_START);
        TrajectoryEvent reasoning = first(channel.events, Kind.REASONING);
        assertThat(reasoning.parentSpanId()).isEqualTo(runStart.spanId());
        assertThat(reasoning.spanId()).isNotEqualTo(runStart.spanId());
        assertThat(reasoning.durationMs()).isNull();
    }

    @Test
    void unbalancedEndIsToleratedAndStillStamps() {
        CapturingChannel channel = new CapturingChannel();
        StampingTrajectoryEmitter emitter = emitter(channel, TrajectoryLevel.FULL, EnumSet.allOf(Kind.class));

        assertThatCode(() -> {
            emitter.emit(TrajectoryDraft.runStart());
            emitter.emit(TrajectoryDraft.toolCallEnd("ghost", "r")); // no matching start
            emitter.emit(TrajectoryDraft.runEnd());
        }).doesNotThrowAnyException();

        assertThat(channel.events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.TOOL_CALL_END, Kind.RUN_END);
        TrajectoryEvent runStart = first(channel.events, Kind.RUN_START);
        TrajectoryEvent ghostEnd = first(channel.events, Kind.TOOL_CALL_END);
        // The orphan end still gets a fresh span hung off the open run span.
        assertThat(ghostEnd.spanId()).isNotNull();
        assertThat(ghostEnd.parentSpanId()).isEqualTo(runStart.spanId());
        // The run span still closes correctly as the root.
        assertThat(first(channel.events, Kind.RUN_END).parentSpanId()).isNull();
    }
}
