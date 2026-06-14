package com.huawei.ascend.bus.forwarding.spi;

import java.util.Objects;

/**
 * Type-safe identifier for a runtime-to-runtime forwarding message.
 *
 * <p>Opaque stable value allocated by the forwarding substrate. Participates in
 * the outbox unique key {@code (tenantId, messageId)} and the inbox dedup key
 * {@code (tenantId, messageId, consumerServiceId)} — see
 * {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §5}.
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding-Runtime} (Stage 7);
 * CLAUDE.md Rule R-C sub-clause .c.
 */
// scope: forwarding substrate — opaque message id; tenant scope lives on the envelope
public record ForwardingMessageId(String value) {
    public ForwardingMessageId {
        Objects.requireNonNull(value, "value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
