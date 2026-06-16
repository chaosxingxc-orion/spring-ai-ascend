package com.huawei.ascend.runtime.engine.spi;

import java.util.Locale;
import java.util.Set;

/**
 * Framework-neutral northbound trajectory event: one observable step of a single
 * invocation. Correlation is layered — {@code tenantId} (the owning tenant) →
 * {@code contextId} (the conversation/session) → {@code taskId} (one call) →
 * {@code seq} (monotonic step order assigned by the runtime, never by the framework).
 * {@code tenantId} also lets every exported span/log carry the mandatory tenant
 * attribute. Optional-tier fields ({@code usage}, {@code reasoning}, {@code finishReason},
 * model-call kinds) are best-effort: populated only when the source framework natively
 * exposes them.
 *
 * <p>The envelope mirrors the agentscope-runtime {@code Event{sequence_number,
 * object, status, error}} shape; {@code usage} mirrors OpenTelemetry {@code gen_ai.*}
 * ({@code provider} = {@code gen_ai.system}, {@code finishReason} =
 * {@code gen_ai.response.finish_reasons}, {@code error.category} = {@code gen_ai.error.type}).
 * Trajectory is emit-only telemetry; it is never read back, and the runtime remains
 * the single source of truth.
 *
 * <p><b>Span model.</b> {@code traceId} (= {@code taskId}), {@code spanId} and
 * {@code parentSpanId} let a consumer rebuild the call tree and export OpenTelemetry
 * spans; the runtime assigns them, never the framework. Span-pair kinds
 * ({@code RUN}/{@code MODEL_CALL}/{@code TOOL_CALL} {@code _START}+{@code _END}) share
 * one {@code spanId}: the {@code _START} carries {@code durationMs == null}, the
 * {@code _END} the elapsed {@code durationMs}; a consumer pairs them by {@code spanId}
 * into one span. Point kinds ({@code REASONING}/{@code ERROR}/{@code PROGRESS}/
 * {@code MODEL_CALL_FIRST_TOKEN}) get a fresh {@code spanId}, {@code durationMs == null}
 * and {@code parentSpanId} = the enclosing open span; a consumer exports them as span
 * events on that parent. {@code parentSpanId} resolves to the nearest <i>emitted</i>
 * ancestor, so a detail level that drops an optional-tier span never leaves a dangling
 * parent. {@code tsEpochMillis} is the event wall-clock time.
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
        String finishReason,
        String parentTaskId,
        String parentTraceId,
        String schemaVersion) {

    /**
     * Current trajectory wire-contract version. The published schema (per-field wire table,
     * the {@code trajectory.level}/{@code trajectory.northbound} A2A metadata keys, and the
     * {@code agent-trajectory} artifact name) is the "Northbound trajectory wire contract"
     * entry in {@code docs/contracts/contract-catalog.md}; bump both in lockstep.
     */
    public static final String SCHEMA_VERSION = "3";

    public enum Kind {
        RUN_START,
        RUN_END,
        MODEL_CALL_START,
        MODEL_CALL_END,
        TOOL_CALL_START,
        TOOL_CALL_END,
        REASONING,
        ERROR,
        PROGRESS,
        MODEL_CALL_FIRST_TOKEN
    }

    /**
     * The cross-framework common denominator every adapter can emit. Kinds outside
     * this set are optional and only surface when the framework exposes them.
     */
    public static final Set<Kind> MANDATORY_KINDS = Set.of(
            Kind.RUN_START, Kind.RUN_END, Kind.TOOL_CALL_START, Kind.TOOL_CALL_END, Kind.ERROR);

    /**
     * Token/latency/model/cost telemetry, aligned to OpenTelemetry {@code gen_ai.usage.*}.
     * {@code provider} is the {@code gen_ai.system} value (e.g. {@code openai}); {@code costMicros}
     * is the computed token cost in millionths of a currency unit (integer to avoid float
     * rounding in billing/FinOps aggregation), null when not computed.
     */
    public record Usage(Integer inputTokens, Integer outputTokens, Double latencyMs, String model,
            String provider, Long costMicros) {}

    /**
     * Error payload. {@code code} is the free-text framework/adapter code (transparent);
     * {@code category} is the stable cross-framework classification a consumer can aggregate on.
     */
    public record ErrorInfo(String code, String message, ErrorCategory category) {}

    /**
     * Structured error classification aligned to OpenTelemetry GenAI {@code gen_ai.error.type}.
     * Orthogonal to the free-text {@code ErrorInfo.code}: the code stays transparent, this is the
     * taxonomy automated triage and dashboards group on.
     */
    public enum ErrorCategory {
        RATE_LIMIT,
        TIMEOUT,
        AUTH,
        INVALID_REQUEST,
        SERVER_ERROR,
        NETWORK,
        CANCELLED,
        UNKNOWN;

        /** Best-effort classification walking the cause chain; never throws, defaults to UNKNOWN. */
        public static ErrorCategory classify(Throwable error) {
            Throwable t = error;
            for (int depth = 0; t != null && depth < 16; t = t.getCause(), depth++) {
                ErrorCategory c = classifyOne(t);
                if (c != UNKNOWN) {
                    return c;
                }
                if (t == t.getCause()) {
                    break;
                }
            }
            return UNKNOWN;
        }

        private static ErrorCategory classifyOne(Throwable t) {
            String cls = t.getClass().getName().toLowerCase(Locale.ROOT);
            String msg = t.getMessage() != null ? t.getMessage().toLowerCase(Locale.ROOT) : "";
            if (cls.contains("timeout") || msg.contains("timed out") || msg.contains("timeout")) {
                return TIMEOUT;
            }
            if (t instanceof InterruptedException || cls.contains("cancellation") || msg.contains("cancel")) {
                return CANCELLED;
            }
            if (msg.contains("429") || msg.contains("rate limit") || msg.contains("quota")
                    || msg.contains("too many requests")) {
                return RATE_LIMIT;
            }
            if (msg.contains("401") || msg.contains("403") || msg.contains("unauthor")
                    || msg.contains("forbidden") || msg.contains("permission denied")) {
                return AUTH;
            }
            if (cls.contains("unknownhost") || cls.contains("connectexception")
                    || cls.contains("socket") || msg.contains("connection refused")) {
                return NETWORK;
            }
            if (t instanceof IllegalArgumentException || msg.contains("invalid") || msg.contains("400")) {
                return INVALID_REQUEST;
            }
            if (msg.contains("500") || msg.contains("502") || msg.contains("503")
                    || msg.contains("internal server error")) {
                return SERVER_ERROR;
            }
            return UNKNOWN;
        }
    }
}
