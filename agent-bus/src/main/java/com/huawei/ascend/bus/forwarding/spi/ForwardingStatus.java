package com.huawei.ascend.bus.forwarding.spi;

/**
 * Outbox / inbox state spaces for the C3 forwarding runtime.
 *
 * <p>Split into {@link Outbox} (sender durable-queue lifecycle) and {@link Inbox}
 * (receiver dedup / audit lifecycle). The state spaces are disjoint by design:
 * outbox tracks a message from enqueue to a terminal ack / dlq / expiry, while
 * inbox tracks receiver-side dedup and consumption. Both are driven by
 * {@code ForwardingStateMachine} (runtime package).
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding-Runtime};
 * {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4}.
 */
public final class ForwardingStatus {
    private ForwardingStatus() {
        throw new AssertionError("ForwardingStatus is a namespace for the Outbox/Inbox enums");
    }

    /** Outbox record lifecycle. Terminal: ACKED, DLQ, EXPIRED. */
    public enum Outbox {
        PENDING, DISPATCHING, ACKED, RETRY_SCHEDULED, DLQ, EXPIRED
    }

    /** Inbox record lifecycle. Terminal: DUPLICATE_SUPPRESSED, CONSUMED, REJECTED. */
    public enum Inbox {
        RECEIVED, DUPLICATE_SUPPRESSED, CONSUMED, REJECTED
    }
}
