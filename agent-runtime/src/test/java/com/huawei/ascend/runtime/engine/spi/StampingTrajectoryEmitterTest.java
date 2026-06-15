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
                new TrajectorySettings(true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
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
    void payloadsAreMaskedAndTruncated() {
        CapturingSink sink = new CapturingSink();
        TrajectorySettings settings = new TrajectorySettings(true,
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
    void firstTokenIsAPointEventCarryingTtftFromEnclosingSpan() {
        CapturingSink sink = new CapturingSink();
        StampingTrajectoryEmitter emitter = emitter(sink, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.firstToken());
        emitter.emit(TrajectoryDraft.runEnd());

        TrajectoryEvent runStart = first(sink.events, Kind.RUN_START);
        TrajectoryEvent firstToken = first(sink.events, Kind.MODEL_CALL_FIRST_TOKEN);
        // Point event: fresh span id, parented to the enclosing open run span.
        assertThat(firstToken.parentSpanId()).isEqualTo(runStart.spanId());
        assertThat(firstToken.spanId()).isNotEqualTo(runStart.spanId());
        // durationMs carries the time-to-first-token measured from the run span start.
        assertThat(firstToken.durationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    void sampleRateZeroDropsTheWholeTrajectory() {
        CapturingSink sink = new CapturingSink();
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 0.0);
        StampingTrajectoryEmitter emitter =
                new StampingTrajectoryEmitter(sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.toolCallStart("search", "q"));
        emitter.emit(TrajectoryDraft.runEnd());

        assertThat(sink.events).isEmpty();
    }

    @Test
    void sampleRateOneKeepsEveryEvent() {
        CapturingSink sink = new CapturingSink();
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 1.0);
        StampingTrajectoryEmitter emitter =
                new StampingTrajectoryEmitter(sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.runEnd());

        assertThat(sink.events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.RUN_END);
    }

    @Test
    void parentLinkageFromScopeIsStampedOnEveryEvent() {
        CapturingSink sink = new CapturingSink();
        RuntimeIdentity child = SCOPE.withParent("parent-task", "parent-ctx");
        StampingTrajectoryEmitter emitter = new StampingTrajectoryEmitter(sink, child,
                new TrajectorySettings(true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256),
                EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());
        emitter.emit(TrajectoryDraft.runEnd());

        // traceId == taskId in this model, so parentTraceId == parentTaskId.
        assertThat(sink.events).isNotEmpty().allSatisfy(e -> {
            assertThat(e.parentTaskId()).isEqualTo("parent-task");
            assertThat(e.parentTraceId()).isEqualTo("parent-task");
        });
    }

    @Test
    void rootScopeHasNoParentLinkage() {
        CapturingSink sink = new CapturingSink();
        StampingTrajectoryEmitter emitter = emitter(sink, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.runStart());

        TrajectoryEvent runStart = first(sink.events, Kind.RUN_START);
        assertThat(runStart.parentTaskId()).isNull();
        assertThat(runStart.parentTraceId()).isNull();
    }

    @Test
    void patternRedactorScrubsCreditCardInArgs() {
        CapturingSink sink = new CapturingSink();
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 1.0,
                new PatternRedactor(), CostCalculator.NONE);
        StampingTrajectoryEmitter emitter =
                new StampingTrajectoryEmitter(sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        // Visa test card number embedded in a string arg
        emitter.emit(TrajectoryDraft.toolCallStart("pay", "charge 4111 1111 1111 1111 now"));

        TrajectoryEvent event = first(sink.events, Kind.TOOL_CALL_START);
        assertThat(String.valueOf(event.args())).doesNotContain("4111 1111 1111 1111");
        assertThat(String.valueOf(event.args())).contains("***");
    }

    @Test
    void faultyRedactorFailsClosedToRedactedMarker() {
        CapturingSink sink = new CapturingSink();
        Redactor faultyRedactor = value -> { throw new RuntimeException("redactor broke"); };
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 1.0,
                faultyRedactor, CostCalculator.NONE);
        StampingTrajectoryEmitter emitter =
                new StampingTrajectoryEmitter(sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.toolCallStart("x", "sensitive payload"));

        // Fail-closed: payload is replaced with the redaction marker, not leaked
        assertThat(String.valueOf(first(sink.events, Kind.TOOL_CALL_START).args())).isEqualTo("***");
    }

    @Test
    void costCalculatorEnrichesUsageOnModelCallEnd() {
        CapturingSink sink = new CapturingSink();
        CostCalculator fixedCalculator = usage -> {
            if (usage == null) return null;
            return new TrajectoryEvent.Usage(usage.inputTokens(), usage.outputTokens(),
                    usage.latencyMs(), usage.model(), "openai", 9999L);
        };
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 1.0,
                Redactor.NONE, fixedCalculator);
        StampingTrajectoryEmitter emitter =
                new StampingTrajectoryEmitter(sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        TrajectoryEvent.Usage rawUsage = new TrajectoryEvent.Usage(100, 50, null, "gpt-4o", null, null);
        emitter.emit(TrajectoryDraft.modelCallEnd(rawUsage, "stop", null));

        TrajectoryEvent event = first(sink.events, Kind.MODEL_CALL_END);
        assertThat(event.usage().provider()).isEqualTo("openai");
        assertThat(event.usage().costMicros()).isEqualTo(9999L);
    }

    @Test
    void payloadRefStoreExternalizesOversizedString() {
        CapturingSink sink = new CapturingSink();
        // Store that captures what was put
        java.util.List<String> stored = new java.util.ArrayList<>();
        PayloadRefStore refStore = payload -> { stored.add(payload); return "payload_ref://sha256abc"; };
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 8, 1.0,
                Redactor.NONE, CostCalculator.NONE, refStore);
        StampingTrajectoryEmitter emitter =
                new StampingTrajectoryEmitter(sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        // String longer than truncateChars=8 should be externalized, not truncated
        emitter.emit(TrajectoryDraft.toolCallStart("x", "a very long argument string"));

        TrajectoryEvent event = first(sink.events, Kind.TOOL_CALL_START);
        // payload_ref map replaces the oversized string
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> refMap = (java.util.Map<String, Object>) event.args();
        assertThat(refMap).containsKey("payload_ref");
        assertThat(refMap.get("payload_ref")).isEqualTo("payload_ref://sha256abc");
        assertThat(stored).hasSize(1).contains("a very long argument string");
    }

    @Test
    void payloadRefStoreFallsBackToTruncationOnWriteFailure() {
        CapturingSink sink = new CapturingSink();
        PayloadRefStore failingStore = payload -> { throw new java.io.UncheckedIOException(
                new java.io.IOException("disk full")); };
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 8, 1.0,
                Redactor.NONE, CostCalculator.NONE, failingStore);
        StampingTrajectoryEmitter emitter =
                new StampingTrajectoryEmitter(sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        emitter.emit(TrajectoryDraft.toolCallStart("x", "a very long argument string that exceeds 8 chars"));

        // Store write failed: should fall back to normal truncation (not crash)
        TrajectoryEvent event = first(sink.events, Kind.TOOL_CALL_START);
        assertThat(String.valueOf(event.args())).startsWith("a very l").contains("…(");
    }

    @Test
    void faultyCostCalculatorFailsSafePreservingOriginalUsage() {
        CapturingSink sink = new CapturingSink();
        CostCalculator faultyCalc = usage -> { throw new RuntimeException("calc broke"); };
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 1.0,
                Redactor.NONE, faultyCalc);
        StampingTrajectoryEmitter emitter =
                new StampingTrajectoryEmitter(sink, SCOPE, settings, EnumSet.allOf(Kind.class));

        TrajectoryEvent.Usage rawUsage = new TrajectoryEvent.Usage(10, 5, null, "gpt-4o", null, null);
        emitter.emit(TrajectoryDraft.modelCallEnd(rawUsage, "stop", null));

        // Fail-safe: the original usage is preserved, not dropped
        TrajectoryEvent event = first(sink.events, Kind.MODEL_CALL_END);
        assertThat(event.usage().inputTokens()).isEqualTo(10);
        assertThat(event.usage().outputTokens()).isEqualTo(5);
    }
}
