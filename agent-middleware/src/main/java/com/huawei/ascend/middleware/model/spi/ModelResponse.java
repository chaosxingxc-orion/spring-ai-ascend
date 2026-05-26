package com.huawei.ascend.middleware.model.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LLM response envelope.
 *
 * <p>Authority: ADR-0121.
 *
 * @param content       assistant message content; never null, may be
 *                      empty if the model only emitted tool calls.
 * @param toolCalls     tool-call requests emitted by the model; never
 *                      null, may be empty. Each carries a
 *                      target {@code skillKey} and JSON argument blob.
 * @param finishReason  why the model stopped
 *                      ({@link ModelFinishReason}).
 * @param usage         token usage; nullable when the provider does
 *                      not report it.
 * @param metadata      provider-specific extras (rate-limit headers,
 *                      cache hit indicators, ...). May be empty.
 */
public record ModelResponse(
        String content,
        List<ToolCall> toolCalls,
        ModelFinishReason finishReason,
        ModelUsage usage,
        Map<String, Object> metadata) {

    public ModelResponse {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(toolCalls, "toolCalls");
        Objects.requireNonNull(finishReason, "finishReason");
        Objects.requireNonNull(metadata, "metadata");
        toolCalls = List.copyOf(toolCalls);
        metadata = Map.copyOf(metadata);
    }

    /**
     * One tool call requested by the model.
     *
     * @param callId    unique identifier for this call within the
     *                  response; used to correlate the tool result
     *                  message in the next turn.
     * @param skillKey  target skill identifier.
     * @param arguments JSON-encoded argument blob (provider-supplied).
     */
    public record ToolCall(String callId, String skillKey, String arguments) {
        public ToolCall {
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(skillKey, "skillKey");
            Objects.requireNonNull(arguments, "arguments");
        }
    }
}
