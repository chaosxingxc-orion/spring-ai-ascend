package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.ErrorCategory;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.ErrorInfo;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Usage;

/**
 * The framework-supplied half of a trajectory event: kind + payload, WITHOUT
 * correlation. {@link AbstractAgentRuntimeHandler} stamps {@code seq}/{@code contextId}/
 * {@code taskId}/{@code schemaVersion} and applies masking before publishing the
 * finished {@link TrajectoryEvent}. Adapters never invent correlation identity.
 */
public record TrajectoryDraft(
        Kind kind,
        String object,
        String name,
        Object args,
        Object result,
        Usage usage,
        Integer attempt,
        Boolean retryable,
        ErrorInfo error,
        String reasoning,
        String finishReason) {

    public static TrajectoryDraft runStart() {
        return new TrajectoryDraft(Kind.RUN_START, "run", null, null, null, null, null, null, null, null, null);
    }

    public static TrajectoryDraft runEnd() {
        return new TrajectoryDraft(Kind.RUN_END, "run", null, null, null, null, null, null, null, null, null);
    }

    public static TrajectoryDraft modelCallStart(Object args) {
        return new TrajectoryDraft(Kind.MODEL_CALL_START, "model_call", null, args, null, null, null, null, null,
                null, null);
    }

    public static TrajectoryDraft modelCallEnd(Usage usage, String finishReason, String reasoning) {
        return new TrajectoryDraft(Kind.MODEL_CALL_END, "model_call", null, null, null, usage, null, null, null,
                reasoning, finishReason);
    }

    public static TrajectoryDraft reasoning(String text) {
        return new TrajectoryDraft(Kind.REASONING, "reasoning", null, null, null, null, null, null, null, text, null);
    }

    public static TrajectoryDraft progress(String delta) {
        return new TrajectoryDraft(Kind.PROGRESS, "progress", null, null, delta, null, null, null, null, null, null);
    }

    /**
     * First streamed token of a model call — a point event the runtime stamps with the
     * time-to-first-token (in {@code durationMs}). Optional tier: only frameworks that stream
     * model output deltas emit it.
     */
    public static TrajectoryDraft firstToken() {
        return new TrajectoryDraft(Kind.MODEL_CALL_FIRST_TOKEN, "first_token", null, null, null, null, null, null,
                null, null, null);
    }

    public static TrajectoryDraft toolCallStart(String name, Object args) {
        return new TrajectoryDraft(Kind.TOOL_CALL_START, "tool_call", name, args, null, null, null, null, null,
                null, null);
    }

    public static TrajectoryDraft toolCallEnd(String name, Object result) {
        return new TrajectoryDraft(Kind.TOOL_CALL_END, "tool_call", name, null, result, null, null, null, null,
                null, null);
    }

    public static TrajectoryDraft error(String name, String code, String message, ErrorCategory category,
            Integer attempt, Boolean retryable) {
        return new TrajectoryDraft(Kind.ERROR, "error", name, null, null, null, attempt, retryable,
                new ErrorInfo(code, message, category), null, null);
    }
}
