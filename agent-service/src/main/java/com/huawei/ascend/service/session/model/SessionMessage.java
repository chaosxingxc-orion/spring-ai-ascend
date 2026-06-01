package com.huawei.ascend.service.session.model;

import com.huawei.ascend.service.schema.Message;
import java.time.Instant;
import java.util.Objects;

/**
 * A message as stored in a {@link Session}'s history.
 *
 * <p>Wraps the canonical {@link Message} schema (role + content parts) with a
 * storage envelope: a stable {@code messageId}, an optional author {@code name},
 * and a {@code createdAt} timestamp. The session layer is a business-data hub;
 * it does not redefine the message shape, it reuses the shared schema so the
 * access, engine and session layers all speak one message type.
 *
 * @param messageId stable id for this stored message; never {@code null}.
 * @param name      optional author/display name; may be {@code null}.
 * @param message   the canonical message payload; never {@code null}.
 * @param createdAt when the message was recorded; defaults to now.
 */
public record SessionMessage(
        String messageId,
        String name,
        Message message,
        Instant createdAt) {

    public SessionMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(message, "message");
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    /** Creates a stored message from a canonical {@link Message}. */
    public static SessionMessage of(String messageId, Message message) {
        return new SessionMessage(messageId, null, message, Instant.now());
    }
}
