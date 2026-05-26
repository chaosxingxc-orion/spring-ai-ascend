package com.huawei.ascend.middleware.model.spi;

import java.util.Objects;

/**
 * One element of a streaming LLM response.
 *
 * <p>Authority: ADR-0129, schema at
 * {@code docs/contracts/model-streaming.v1.yaml}.
 *
 * <p>Emitted by {@link ModelGateway#stream(ModelInvocation)} as a
 * finite ordered sequence. A successful stream MUST contain exactly one
 * {@link Complete} element which MUST be the last element; all preceding
 * elements are {@link ContentDelta} or {@link ToolCallDelta} fragments
 * in arrival order. A cancelled stream may close before Complete, and
 * provider/runtime errors surface as exceptions from the stream.
 *
 * <p>Hook binding (ADR-0073): {@code HookPoint.BEFORE_LLM} fires
 * once before stream open; {@code HookPoint.AFTER_LLM} fires once on
 * the terminal {@link Complete} chunk with
 * {@link Complete#finalResponse()}.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package siblings.
 */
public sealed interface ModelResponseChunk
        permits ModelResponseChunk.ContentDelta,
                ModelResponseChunk.ToolCallDelta,
                ModelResponseChunk.Complete {

    /**
     * Incremental assistant content fragment.
     *
     * @param deltaText incremental assistant content; never null, may
     *                  be empty between providers.
     */
    record ContentDelta(String deltaText) implements ModelResponseChunk {
        public ContentDelta {
            Objects.requireNonNull(deltaText, "deltaText");
        }
    }

    /**
     * Incremental tool-call argument fragment. Providers emit zero
     * or more {@code ToolCallDelta} chunks per logical call, with
     * arguments accumulating across the {@code argumentsDelta} values
     * sharing the same {@code callId}.
     *
     * @param callId         unique identifier for this call within the
     *                       response; matches the terminal
     *                       {@code Complete.finalResponse().toolCalls()}
     *                       entry's {@code callId}.
     * @param skillKey       target skill identifier (SkillRef.skillKey).
     * @param argumentsDelta partial JSON fragment of arguments; never
     *                       null, may be empty.
     */
    record ToolCallDelta(String callId, String skillKey, String argumentsDelta)
            implements ModelResponseChunk {
        public ToolCallDelta {
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(skillKey, "skillKey");
            Objects.requireNonNull(argumentsDelta, "argumentsDelta");
        }
    }

    /**
     * Terminal chunk carrying the fully assembled response.
     *
     * <p>Emitted exactly once on successful end-of-stream. The assembled
     * {@link ModelResponse} surfaces {@code finishReason},
     * {@code usage}, the complete content string, and the assembled
     * tool calls — equivalent to what
     * {@link ModelGateway#invoke(ModelInvocation)} would have
     * returned for the same {@code ModelInvocation}.
     *
     * @param finalResponse the assembled response; never null.
     */
    record Complete(ModelResponse finalResponse) implements ModelResponseChunk {
        public Complete {
            Objects.requireNonNull(finalResponse, "finalResponse");
        }
    }
}
