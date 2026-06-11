package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.ErrorInfo;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Turns adapter-supplied {@link TrajectoryDraft}s into fully-stamped
 * {@link TrajectoryEvent}s: assigns a monotonic {@code seq}, the {@code contextId}/
 * {@code taskId} correlation, the {@code traceId}/{@code spanId}/{@code parentSpanId}
 * span tree, wall-clock {@code tsEpochMillis}/{@code durationMs}, and the schema
 * version; drops kinds the handler does not support or that the detail level excludes;
 * and masks payloads. One instance per invocation — its mutable {@code seq} and span
 * stack are never shared across calls.
 *
 * <p>Span ids are derived from a per-invocation stack so adapters keep emitting only
 * {@code _START}/{@code _END}/point drafts: a {@code _START} pushes a child of the
 * current open span, the matching {@code _END} reuses that span's id and reports its
 * duration, and a point event hangs off the nearest open span. The stack is maintained
 * for <i>every</i> draft, even a level-filtered one, so dropping an optional-tier span
 * never desyncs the parent chain of the events that publish.
 */
public final class StampingTrajectoryEmitter implements TrajectoryEmitter {

    private static final Set<Kind> OPTIONAL_TIER =
            Set.of(Kind.MODEL_CALL_START, Kind.MODEL_CALL_END, Kind.REASONING, Kind.PROGRESS);

    private final TrajectoryChannel channel;
    private final String tenantId;
    private final String contextId;
    private final String taskId;
    private final TrajectorySettings settings;
    private final Set<Kind> supportedKinds;
    private final AtomicLong seq = new AtomicLong(0L);
    private final Deque<SpanFrame> spanStack = new ArrayDeque<>();

    public StampingTrajectoryEmitter(TrajectoryChannel channel, RuntimeIdentity scope,
            TrajectorySettings settings, Set<Kind> supportedKinds) {
        this.channel = channel;
        this.tenantId = scope != null ? scope.tenantId() : null;
        this.contextId = scope != null ? scope.sessionId() : null;
        this.taskId = scope != null ? scope.taskId() : null;
        this.settings = settings;
        this.supportedKinds = supportedKinds;
    }

    @Override
    public void emit(TrajectoryDraft draft) {
        if (draft == null) {
            return;
        }
        Kind kind = draft.kind();
        boolean publish = allows(kind);
        long now = System.currentTimeMillis();
        // Maintain the span stack for every draft, even a filtered one, so a dropped
        // optional-tier span never desyncs the parent chain of what does publish.
        SpanInfo span = computeSpan(kind, publish, now);
        if (!publish) {
            return;
        }
        Object args = TrajectoryMasking.mask(draft.args(), settings.level(),
                settings.maskKeyPattern(), settings.truncateChars());
        Object result = TrajectoryMasking.mask(draft.result(), settings.level(),
                settings.maskKeyPattern(), settings.truncateChars());
        Object reasoning = TrajectoryMasking.mask(draft.reasoning(), settings.level(),
                settings.maskKeyPattern(), settings.truncateChars());
        ErrorInfo error = maskError(draft.error());
        channel.publish(new TrajectoryEvent(
                seq.getAndIncrement(),
                kind,
                now,
                span.durationMs(),
                taskId,
                span.spanId(),
                span.parentSpanId(),
                tenantId,
                contextId,
                taskId,
                draft.object(),
                draft.name(),
                args,
                result,
                draft.usage(),
                draft.attempt(),
                draft.retryable(),
                error,
                reasoning != null ? String.valueOf(reasoning) : null,
                TrajectoryEvent.SCHEMA_VERSION));
    }

    /**
     * Derives the span ids for one draft and advances the span stack. A {@code _START}
     * pushes a child of the nearest emitted ancestor; the matching {@code _END} reuses
     * that span's id (and its original parent) and reports the elapsed duration; a point
     * event hangs off the nearest emitted ancestor with a fresh id. {@code published}
     * records whether this START will be emitted, so a filtered span is skipped when a
     * later event resolves its parent. Never throws — trajectory must not break the run.
     */
    private SpanInfo computeSpan(Kind kind, boolean published, long now) {
        return switch (kind) {
            case RUN_START, MODEL_CALL_START, TOOL_CALL_START -> {
                String parent = currentPublishedSpanId();
                String spanId = newSpanId();
                spanStack.push(new SpanFrame(spanId, parent, now, kind, published));
                yield new SpanInfo(spanId, parent, null);
            }
            case RUN_END, MODEL_CALL_END, TOOL_CALL_END -> {
                SpanFrame frame = matchAndPop(startOf(kind));
                if (frame != null) {
                    yield new SpanInfo(frame.spanId(), frame.parentSpanId(), now - frame.startMillis());
                }
                yield new SpanInfo(newSpanId(), currentPublishedSpanId(), null);
            }
            default -> new SpanInfo(newSpanId(), currentPublishedSpanId(), null);
        };
    }

    /** Nearest open span that was itself emitted — skips level-filtered ancestors. */
    private String currentPublishedSpanId() {
        for (SpanFrame frame : spanStack) {
            if (frame.published()) {
                return frame.spanId();
            }
        }
        return null;
    }

    /** Pops the matching open START (top first, else nearest below) — tolerant of unbalanced ends. */
    private SpanFrame matchAndPop(Kind startKind) {
        if (startKind == null) {
            return null;
        }
        SpanFrame top = spanStack.peek();
        if (top != null && top.startKind() == startKind) {
            return spanStack.pop();
        }
        for (Iterator<SpanFrame> it = spanStack.iterator(); it.hasNext(); ) {
            SpanFrame frame = it.next();
            if (frame.startKind() == startKind) {
                it.remove();
                return frame;
            }
        }
        return null;
    }

    private static Kind startOf(Kind endKind) {
        return switch (endKind) {
            case RUN_END -> Kind.RUN_START;
            case MODEL_CALL_END -> Kind.MODEL_CALL_START;
            case TOOL_CALL_END -> Kind.TOOL_CALL_START;
            default -> null;
        };
    }

    private static String newSpanId() {
        return String.format("%016x", ThreadLocalRandom.current().nextLong());
    }

    /** One open span: its id, the parent it was opened under, its start time, and whether it emits. */
    private record SpanFrame(String spanId, String parentSpanId, long startMillis, Kind startKind, boolean published) {}

    private record SpanInfo(String spanId, String parentSpanId, Long durationMs) {}

    /** Free-text error messages can embed secrets; run the message through the same masker. */
    private ErrorInfo maskError(ErrorInfo error) {
        if (error == null || error.message() == null) {
            return error;
        }
        Object masked = TrajectoryMasking.mask(error.message(), settings.level(),
                settings.maskKeyPattern(), settings.truncateChars());
        return new ErrorInfo(error.code(), masked != null ? String.valueOf(masked) : null);
    }

    private boolean allows(Kind kind) {
        if (kind == null || !supportedKinds.contains(kind)) {
            return false;
        }
        // Optional tier (model calls, reasoning) only at FULL detail.
        return settings.level() == TrajectoryLevel.FULL || !OPTIONAL_TIER.contains(kind);
    }
}
