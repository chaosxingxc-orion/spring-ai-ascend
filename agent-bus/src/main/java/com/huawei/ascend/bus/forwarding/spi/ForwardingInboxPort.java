package com.huawei.ascend.bus.forwarding.spi;

/**
 * Receiver-side dedup / idempotency / audit port for the C3 inbox substrate.
 *
 * <p>Dedup key is {@code (tenantId, messageId, consumerServiceId)}: distinct
 * consumers dedup independently. Implementations MUST scope every operation by
 * {@code tenantId} (Rule R-C.c; cross-tenant reads fail explicitly, never fall
 * back) and MUST call {@code ForwardingStateMachine} to validate transitions.
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding-Runtime};
 * {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4.2/§8}.
 */
public interface ForwardingInboxPort {

    /**
     * Receive an envelope on the consumer side, dedup, and return the resulting
     * inbox status. First arrival → {@link ForwardingStatus.Inbox#RECEIVED};
     * duplicate {@code (tenantId, messageId, consumerServiceId)} →
     * {@link ForwardingStatus.Inbox#DUPLICATE_SUPPRESSED}.
     */
    ForwardingStatus.Inbox receive(ForwardingEnvelope envelope,
                                   String consumerServiceId, long nowMillisEpoch);

    /** Mark a RECEIVED entry CONSUMED (terminal). */
    ForwardingStatus.Inbox markConsumed(ForwardingMessageId id, String tenantId,
                                        String consumerServiceId);

    /** Mark a RECEIVED entry REJECTED (terminal) with a failure code. */
    ForwardingStatus.Inbox markRejected(ForwardingMessageId id, String tenantId,
                                        String consumerServiceId, ForwardingFailureCode code);

    /** Current status of an inbox entry (tenant-scoped). */
    ForwardingStatus.Inbox statusOf(ForwardingMessageId id, String tenantId,
                                    String consumerServiceId);
}
