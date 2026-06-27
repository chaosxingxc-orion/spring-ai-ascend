package com.huawei.ascend.examples.workmate.team.mailbox;

import java.util.UUID;

/**
 * An immutable message delivered through the {@link TeamMailbox}.
 *
 * <p>{@code to} == {@link #BROADCAST} ("*") means the message fans out to every member
 * except the sender. Otherwise it is a point-to-point message to a single recipient
 * (a member id or the leader id).</p>
 *
 * @param messageId     unique id
 * @param teamRunId     parent team run id (one mailbox per team run)
 * @param from          sender member id (or leader id)
 * @param to            recipient member id, or {@link #BROADCAST} for fan-out
 * @param kind          message kind
 * @param body          message content
 * @param summary       optional short summary (≤ a few hundred chars), may be {@code null}
 * @param correlationId optional correlation id to thread request/response, may be {@code null}
 * @param createdAtMs   creation epoch millis
 */
public record MailboxMessage(
        String messageId,
        String teamRunId,
        String from,
        String to,
        MailboxMessageKind kind,
        String body,
        String summary,
        String correlationId,
        long createdAtMs) {

    /** Recipient token that marks a broadcast to all members except the sender. */
    public static final String BROADCAST = "*";

    public MailboxMessage {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId is required");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("to is required (use BROADCAST for fan-out)");
        }
        kind = kind == null ? MailboxMessageKind.MESSAGE : kind;
        body = body == null ? "" : body;
    }

    public boolean isBroadcast() {
        return BROADCAST.equals(to);
    }

    /** Convenience factory generating a random {@code messageId} and current timestamp. */
    public static MailboxMessage create(
            String teamRunId,
            String from,
            String to,
            MailboxMessageKind kind,
            String body,
            String summary,
            String correlationId) {
        return new MailboxMessage(
                UUID.randomUUID().toString(),
                teamRunId,
                from,
                to,
                kind,
                body,
                summary,
                correlationId,
                System.currentTimeMillis());
    }
}
