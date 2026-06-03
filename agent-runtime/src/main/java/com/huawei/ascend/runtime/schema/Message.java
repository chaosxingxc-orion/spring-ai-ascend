package com.huawei.ascend.service.schema;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical conversational message shared across agent-service modules.
 *
 * <p>Aligned with the agentscope-runtime {@code Message} shape (a {@link Role}
 * plus a list of {@link Content} parts). This is the one message type the
 * access, session, task-control and engine layers exchange, replacing the
 * per-module message records that previously drifted apart.
 *
 * @param role     the message author role; never {@code null}.
 * @param content  ordered content parts; never {@code null}, may be empty.
 * @param metadata optional message-level attributes; never {@code null}.
 */
public record Message(
        Role role,
        List<Content> content,
        Map<String, Object> metadata) {

    public Message {
        Objects.requireNonNull(role, "role");
        content = content == null ? List.of() : List.copyOf(content);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Creates a single-text-part message for the given role. */
    public static Message ofText(Role role, String text) {
        return new Message(role, List.of(Content.text(text)), Map.of());
    }

    /** Convenience for a user text message. */
    public static Message user(String text) {
        return ofText(Role.USER, text);
    }

    /** Convenience for an assistant text message. */
    public static Message assistant(String text) {
        return ofText(Role.ASSISTANT, text);
    }

    /**
     * Concatenates the text of all text parts. Non-text parts are skipped.
     * Returns an empty string when there is no textual content.
     */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Content part : content) {
            if (part.isText()) {
                sb.append(part.asText());
            }
        }
        return sb.toString();
    }
}
