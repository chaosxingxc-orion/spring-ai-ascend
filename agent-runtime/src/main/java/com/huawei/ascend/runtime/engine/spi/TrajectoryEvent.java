package com.huawei.ascend.runtime.engine.spi;

import java.util.Set;

/**
 * Framework-neutral northbound trajectory event: one observable step of a single
 * invocation. Correlation is layered — {@code tenantId} (the owning tenant) →
 * {@code contextId} (the conversation/session) → {@code taskId} (one call) →
 * {@code seq} (monotonic step order assigned by the runtime, never by the framework).
 * {@code tenantId} also lets every exported span/log carry the mandatory tenant
 * attribute. Optional-tier fields ({@code usage}, {@code reasoning}, model-call kinds)
 * are best-effort: populated only when the source framework natively exposes them.
 *
 * <p>The envelope mirrors the agentscope-runtime {@code Event{sequence_number,
 * object, status, error}} shape; {@code usage} mirrors OpenTelemetry {@code gen_ai.*}.
 * Trajectory is emit-only telemetry; it is never read back, and the runtime remains
 * the single source of truth.
 *
 * <p><b>Span model.</b> {@code traceId} (= {@code taskId}), {@code spanId} and
 * {@code parentSpanId} let a consumer rebuild the call tree and export OpenTelemetry
 * spans; the runtime assigns them, never the framework. Span-pair kinds
 * ({@code RUN}/{@code MODEL_CALL}/{@code TOOL_CALL} {@code _START}+{@code _END}) share
 * one {@code spanId}: the {@code _START} carries {@code durationMs == null}, the
 * {@code _END} the elapsed {@code durationMs}; a consumer pairs them by {@code spanId}
 * into one span. Point kinds ({@code REASONING}/{@code ERROR}/{@code PROGRESS}) get a
 * fresh {@code spanId}, {@code durationMs == null} and {@code parentSpanId} = the
 * enclosing open span; a consumer exports them as span events on that parent.
 * {@code parentSpanId} resolves to the nearest <i>emitted</i> ancestor, so a detail
 * level that drops an optional-tier span never leaves a dangling parent.
 * {@code tsEpochMillis} is the event wall-clock time.
 */
public record TrajectoryEvent(
        long seq,
        Kind kind,
        long tsEpochMillis,
        Long durationMs,
        String traceId,
        String spanId,
        String parentSpanId,
        String tenantId,
        String contextId,
        String taskId,
        String object,
        String name,
        Object args,
        Object result,
        Usage usage,
        Integer attempt,
        Boolean retryable,
        ErrorInfo error,
        String reasoning,
        String schemaVersion) {

    /** Current trajectory contract version; bump in lockstep with the published schema. */
    public static final String SCHEMA_VERSION = "2";

    public enum Kind {
        RUN_START,
        RUN_END,
        MODEL_CALL_START,
        MODEL_CALL_END,
        TOOL_CALL_START,
        TOOL_CALL_END,
        REASONING,
        ERROR,
        PROGRESS
    }

    /**
     * The cross-framework common denominator every adapter can emit. Kinds outside
     * this set are optional and only surface when the framework exposes them.
     */
    public static final Set<Kind> MANDATORY_KINDS = Set.of(
            Kind.RUN_START, Kind.RUN_END, Kind.TOOL_CALL_START, Kind.TOOL_CALL_END, Kind.ERROR);

    /** Token/latency/model telemetry, aligned to OpenTelemetry {@code gen_ai.usage.*}. */
    public record Usage(Integer inputTokens, Integer outputTokens, Double latencyMs, String model) {}

    public record ErrorInfo(String code, String message) {}
}
