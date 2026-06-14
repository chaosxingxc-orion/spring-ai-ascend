package com.huawei.ascend.bus.forwarding.spi;

/**
 * Sender-side durable-queue port for the C3 outbox substrate.
 *
 * <p>The port abstracts durable outbox storage so Stage 7 can ship the domain
 * model, state machine and harness without a real database — Stage 8 provides
 * the JDBC / persistent implementation. Implementations MUST call
 * {@code ForwardingStateMachine} to validate a transition before persisting, and
 * MUST scope every operation by {@code tenantId} (tenant isolation, Rule R-C.c;
 * cross-tenant reads fail explicitly, never fall back).
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding-Runtime};
 * {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4.1/§8}.
 */
public interface ForwardingOutboxPort {

    /**
     * Enqueue an envelope into the outbox and return the synchronous ack
     * receipt. A duplicate {@code (tenantId, messageId)} returns an
     * already-accepted receipt without re-enqueueing.
     */
    ForwardingReceipt enqueue(ForwardingEnvelope envelope, long nowMillisEpoch);

    /** Transition an entry to DISPATCHING (PENDING → DISPATCHING). */
    ForwardingStatus.Outbox markDispatching(ForwardingMessageId id, String tenantId);

    /** Transition an entry to ACKED (DISPATCHING → ACKED, terminal). */
    ForwardingStatus.Outbox markAcked(ForwardingMessageId id, String tenantId);

    /** Schedule a retry (DISPATCHING/RETRY_SCHEDULED → RETRY_SCHEDULED). */
    ForwardingStatus.Outbox scheduleRetry(ForwardingMessageId id, String tenantId,
                                          ForwardingFailureCode code, long nextAttemptAtMillisEpoch);

    /** Move an entry to DLQ (terminal). */
    ForwardingStatus.Outbox moveToDlq(ForwardingMessageId id, String tenantId,
                                      ForwardingFailureCode code);

    /** Mark an entry EXPIRED (terminal) — deadline exceeded. */
    ForwardingStatus.Outbox markExpired(ForwardingMessageId id, String tenantId);

    /** Current status of an outbox entry (tenant-scoped). */
    ForwardingStatus.Outbox statusOf(ForwardingMessageId id, String tenantId);
}
