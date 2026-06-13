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
import org.junit.jupiter.api.Test;

/** Span-tree derivation, timestamps, and capability-filter balance of the stamper. */
class StampingTrajectoryEmitterTest {

    private static final RuntimeIdentity SCOPE = new RuntimeIdentity("tenant", "user", "sess", "task1", "agent");

    /** Collects accepted events synchronously on the emitting thread. */
    private static final class CapturingSink implements TrajectorySink {
        final List<TrajectoryEvent> events = new ArrayList<>();

        @Override public void accept(TrajectoryEvent event) { events.add(event); }
    }

    private static StampingTrajectoryEmitter emitter(CapturingSink sink, Set<Kind> kinds) {
        TrajectorySettings settings =
                TrajectorySettings.basic(true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        return new StampingTrajectoryEmitter(sink, SCOPE, settings, kinds);
    }

    private static TrajectoryEvent first(List<TrajectoryEvent> events, Kind kind) {
        return events.stream().filter(e -> e.kind() == kind).findFirst().orElseThrow();
    }

    @Test
    void spanPairsShareIdAndChainParents() {
        CapturingSink sink = new CapturingSink();
        StampingTrajectoryEmitter emitter = emitter(sink, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.toolCallStart("search", "q"));
        emitter.emit(TrajectoryDraft.toolCallEnd("search", "r"));
        emitter.emit(TrajectoryDraft.runEnd());

        TrajectoryEvent runStart = first(sink.events, Kind.RUN_START);
        TrajectoryEvent runEnd = first(sink.events, Kind.RUN_END);
        TrajectoryEvent toolStart = first(sink.events, Kind.TOOL_CALL_START);
        TrajectoryEvent toolEnd = first(sink.events, Kind.TOOL_CALL_END);

        // Root span has no parent; START/END of a span share one id.
        assertThat(runStart.parentSpanId()).isNull();
        assertThat(runEnd.parentSpanId()).isNull();
        assertThat(runEnd.spanId()).isEqualTo(runStart.spanId());
        assertThat(toolEnd.spanId()).isEqualTo(toolStart.spanId());
        // The tool span nests under the run span.
        assertThat(toolStart.parentSpanId()).isEqualTo(runStart.spanId());
        assertThat(toolEnd.parentSpanId()).isEqualTo(runStart.spanId());
        // traceId is the task id, tenantId the owning tenant, for every event.
        assertThat(sink.events).allSatisfy(e -> {
            assertThat(e.traceId()).isEqualTo("task1");
            assertThat(e.tenantId()).isEqualTo("tenant");
        });
    }

    @Test
    void endsCarryDurationStartsDoNot() {
        CapturingSink sink = new CapturingSink();
        StampingTrajectoryEmitter emitter = emitter(sink, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.runEnd());

        TrajectoryEvent runStart = first(sink.events, Kind.RUN_START);
        TrajectoryEvent runEnd = first(sink.events, Kind.RUN_END);
        assertThat(runStart.tsEpochMillis()).isPositive();
        assertThat(runEnd.tsEpochMillis()).isPositive();
        assertThat(runStart.durationMs()).isNull();
        assertThat(runEnd.durationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    void filteredUnsupportedKindKeepsParentChainBalanced() {
        CapturingSink sink = new CapturingSink();
        // The handler advertises only the mandatory core: MODEL_CALL_* drafts are dropped.
        StampingTrajectoryEmitter emitter = emitter(sink, TrajectoryEvent.MANDATORY_KINDS);

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.modelCallStart("in"));      // dropped, but stack must stay balanced
        emitter.emit(TrajectoryDraft.toolCallStart("search", "q"));
        emitter.emit(TrajectoryDraft.toolCallEnd("search", "r"));
        emitter.emit(TrajectoryDraft.modelCallEnd(null, "stop", null)); // dropped
        emitter.emit(TrajectoryDraft.runEnd());

        assertThat(sink.events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.TOOL_CALL_START, Kind.TOOL_CALL_END, Kind.RUN_END);
        assertThat(sink.events).extracting(TrajectoryEvent::seq).containsExactly(0L, 1L, 2L, 3L);
        // The tool span parents to the RUN span, NOT the dropped (unpublished) model span.
        TrajectoryEvent runStart = first(sink.events, Kind.RUN_START);
        TrajectoryEvent toolStart = first(sink.events, Kind.TOOL_CALL_START);
        assertThat(toolStart.parentSpanId()).isEqualTo(runStart.spanId());
    }

    @Test
    void pointEventHangsOffOpenSpan() {
        CapturingSink sink = new CapturingSink();
        StampingTrajectoryEmitter emitter = emitter(sink, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.reasoning("thinking"));
        emitter.emit(TrajectoryDraft.runEnd());

        TrajectoryEvent runStart = first(sink.events, Kind.RUN_START);
        TrajectoryEvent reasoning = first(sink.events, Kind.REASONING);
        assertThat(reasoning.parentSpanId()).isEqualTo(runStart.spanId());
        assertThat(reasoning.spanId()).isNotEqualTo(runStart.spanId());
        assertThat(reasoning.durationMs()).isNull();
    }

    @Test
    void unbalancedEndIsToleratedAndStillStamps() {
        CapturingSink sink = new CapturingSink();
        StampingTrajectoryEmitter emitter = emitter(sink, EnumSet.allOf(Kind.class));

        assertThatCode(() -> {
            emitter.emit(TrajectoryDraft.runStart());
            emitter.emit(TrajectoryDraft.toolCallEnd("ghost", "r")); // no matching start
            emitter.emit(TrajectoryDraft.runEnd());
        }).doesNotThrowAnyException();

        assertThat(sink.events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.TOOL_CALL_END, Kind.RUN_END);
        TrajectoryEvent runStart = first(sink.events, Kind.RUN_START);
        TrajectoryEvent ghostEnd = first(sink.events, Kind.TOOL_CALL_END);
        // The orphan end still gets a fresh span hung off the open run span.
        assertThat(ghostEnd.spanId()).isNotNull();
        assertThat(ghostEnd.parentSpanId()).isEqualTo(runStart.spanId());
        // The run span still closes correctly as the root.
        assertThat(first(sink.events, Kind.RUN_END).parentSpanId()).isNull();
    }

    @Test
    void maskErrorPreservesCategory() {
        CapturingSink sink = new CapturingSink();
        TrajectorySettings settings = TrajectorySettings.basic(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        StampingTrajectoryEmitter emitter =
                new StampingTrajectoryEmitter(sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        // Emit an error with a non-UNKNOWN category; maskError must not lose it.
        emitter.emit(TrajectoryDraft.error(null, "RATE_LIMIT", "quota hit",
                ErrorCategory.RATE_LIMITED, 1, true));

        TrajectoryEvent event = first(sink.events, Kind.ERROR);
        assertThat(event.error().category()).isEqualTo(ErrorCategory.RATE_LIMITED);
    }

    @Test
    void finishReasonFlowsThroughUnmaskedOnModelCallEnd() {
        CapturingSink sink = new CapturingSink();
        StampingTrajectoryEmitter emitter = emitter(sink, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.modelCallStart("in"));
        emitter.emit(TrajectoryDraft.modelCallEnd(null, "stop", null));
        emitter.emit(TrajectoryDraft.runEnd());

        TrajectoryEvent modelEnd = first(sink.events, Kind.MODEL_CALL_END);
        // finishReason is a controlled token — not free-text — so it is passed unmasked.
        assertThat(modelEnd.finishReason()).isEqualTo("stop");
        assertThat(modelEnd.result()).isNull();
    }

    @Test
    void payloadsAreMaskedAndTruncated() {
        CapturingSink sink = new CapturingSink();
        TrajectorySettings settings = TrajectorySettings.basic(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 8);
        StampingTrajectoryEmitter emitter =
                new StampingTrajectoryEmitter(sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.toolCallStart("search",
                java.util.Map.of("api_key", "sk-very-secret", "query", "a very long question indeed")));

        TrajectoryEvent event = first(sink.events, Kind.TOOL_CALL_START);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> args = (java.util.Map<String, Object>) event.args();
        assertThat(args.get("api_key")).isEqualTo("***");
        assertThat(String.valueOf(args.get("query"))).startsWith("a very l").contains("…(");
    }

    @Test
    void parentLinkageIsStampedOntoEveryEvent() {
        CapturingSink sink = new CapturingSink();
        TrajectorySettings settings =
                TrajectorySettings.basic(true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        StampingTrajectoryEmitter emitter = new StampingTrajectoryEmitter(
                sink, SCOPE, settings, EnumSet.allOf(Kind.class), "parent-task-99", "parent-trace-77");

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.toolCallStart("search", "q"));
        emitter.emit(TrajectoryDraft.toolCallEnd("search", "r"));
        emitter.emit(TrajectoryDraft.runEnd());

        assertThat(sink.events).allSatisfy(e -> {
            assertThat(e.parentTaskId()).isEqualTo("parent-task-99");
            assertThat(e.parentTraceId()).isEqualTo("parent-trace-77");
        });
    }

    @Test
    void nullParentLinkageProducesNullFieldsOnEvents() {
        CapturingSink sink = new CapturingSink();
        StampingTrajectoryEmitter emitter = emitter(sink, EnumSet.of(Kind.RUN_START));

        emitter.emit(TrajectoryDraft.runStart());

        assertThat(sink.events).hasSize(1);
        assertThat(sink.events.get(0).parentTaskId()).isNull();
        assertThat(sink.events.get(0).parentTraceId()).isNull();
    }

    @Test
    void sampleRateZeroDropsInnerKindsButKeepsSkeletonAndErrors() {
        CapturingSink sink = new CapturingSink();
        TrajectorySettings settings = new TrajectorySettings(
                true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 0.0);
        StampingTrajectoryEmitter emitter = new StampingTrajectoryEmitter(
                sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.modelCallStart("in"));
        emitter.emit(TrajectoryDraft.toolCallStart("search", "q"));
        emitter.emit(TrajectoryDraft.toolCallEnd("search", "r"));
        emitter.emit(TrajectoryDraft.modelCallEnd(null, "stop", null));
        emitter.emit(TrajectoryDraft.reasoning("thinking"));
        emitter.emit(TrajectoryDraft.error(null, "ERR", "oops", ErrorCategory.UNKNOWN, 1, false));
        emitter.emit(TrajectoryDraft.runEnd());

        assertThat(sink.events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.ERROR, Kind.RUN_END);
        // The always-kept ERROR resolves its parent past the dropped inner spans to the RUN span.
        assertThat(first(sink.events, Kind.ERROR).parentSpanId())
                .isEqualTo(first(sink.events, Kind.RUN_START).spanId());
    }

    @Test
    void sampleRateOneEmitsAllKinds() {
        CapturingSink sink = new CapturingSink();
        TrajectorySettings settings = new TrajectorySettings(
                true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 1.0);
        StampingTrajectoryEmitter emitter = new StampingTrajectoryEmitter(
                sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.modelCallStart("in"));
        emitter.emit(TrajectoryDraft.toolCallStart("search", "q"));
        emitter.emit(TrajectoryDraft.toolCallEnd("search", "r"));
        emitter.emit(TrajectoryDraft.modelCallEnd(null, "stop", null));
        emitter.emit(TrajectoryDraft.reasoning("thinking"));
        emitter.emit(TrajectoryDraft.error(null, "ERR", "oops", ErrorCategory.UNKNOWN, 1, false));
        emitter.emit(TrajectoryDraft.runEnd());

        assertThat(sink.events).extracting(TrajectoryEvent::kind).containsExactly(
                Kind.RUN_START, Kind.MODEL_CALL_START, Kind.TOOL_CALL_START,
                Kind.TOOL_CALL_END, Kind.MODEL_CALL_END, Kind.REASONING,
                Kind.ERROR, Kind.RUN_END);
    }

    @Test
    void sampleRateZeroSpanStackRemainsBalancedForAlwaysKeptEvents() {
        CapturingSink sink = new CapturingSink();
        TrajectorySettings settings = new TrajectorySettings(
                true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 0.0);
        StampingTrajectoryEmitter emitter = new StampingTrajectoryEmitter(
                sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.toolCallStart("search", "q")); // dropped, stack still maintained
        emitter.emit(TrajectoryDraft.toolCallEnd("search", "r"));   // dropped
        emitter.emit(TrajectoryDraft.runEnd());

        // Only always-kept events published; RUN_END must still carry null parent (root span).
        assertThat(sink.events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.RUN_END);
        TrajectoryEvent runStart = first(sink.events, Kind.RUN_START);
        TrajectoryEvent runEnd = first(sink.events, Kind.RUN_END);
        assertThat(runEnd.spanId()).isEqualTo(runStart.spanId());
        assertThat(runEnd.parentSpanId()).isNull();
    }
}
