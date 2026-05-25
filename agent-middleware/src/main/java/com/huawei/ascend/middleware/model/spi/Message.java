package com.huawei.ascend.middleware.model.spi;

import java.util.List;
import java.util.Objects;

/**
 * Conversation turn in a {@link ModelInvocation}.
 *
 * <p>Authority: ADR-0121. Mirrors Spring AI's message taxonomy at
 * the platform boundary so the reference adapter (Wave C1) is a
 * straight delegation.
 */
public sealed interface Message
        permits Message.SystemMessage,
                Message.UserMessage,
                Message.AssistantMessage,
                Message.ToolResultMessage {

    /** Free-form content; never null. */
    String content();

    /** Role discriminator ({@code system} | {@code user} | {@code assistant} | {@code tool}). */
    String role();

    record SystemMessage(String content) implements Message {
        public SystemMessage {
            Objects.requireNonNull(content, "content");
        }
        @Override public String role() { return "system"; }
    }

    record UserMessage(String content) implements Message {
        public UserMessage {
            Objects.requireNonNull(content, "content");
        }
        @Override public String role() { return "user"; }
    }

    /**
     * @param content    free-form assistant text; may be empty when
     *                   the assistant only emitted tool calls.
     * @param toolCalls  tool-call requests; never null, may be empty.
     */
    record AssistantMessage(String content, List<ModelResponse.ToolCall> toolCalls)
            implements Message {
        public AssistantMessage {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(toolCalls, "toolCalls");
            toolCalls = List.copyOf(toolCalls);
        }
        @Override public String role() { return "assistant"; }
    }

    /**
     * Result of a prior tool call, fed back into the next turn.
     *
     * @param callId  matches {@code ModelResponse.ToolCall.callId} from
     *                the prior assistant turn.
     * @param content tool result (JSON or free-form text); never null.
     */
    record ToolResultMessage(String callId, String content) implements Message {
        public ToolResultMessage {
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(content, "content");
        }
        @Override public String role() { return "tool"; }
    }
}
