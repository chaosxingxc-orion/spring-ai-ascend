package com.huawei.ascend.bus.forwarding.spi;

/**
 * Coordinator that drives a forwarding envelope through the outbox lifecycle —
 * enqueue → DISPATCHING → ACK / RETRY / DLQ / EXPIRED — delegating storage to
 * {@link ForwardingOutboxPort} and transitions to {@code ForwardingStateMachine}.
 *
 * <p>Stage 7 ships this interface plus an in-memory test double; the real
 * delivery binding (dispatcher → receiver transport) is Stage 8. The dispatcher
 * never bypasses {@link ForwardingRouteHandle} to a physical endpoint, and never
 * writes Task execution state.
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §3/§8}.
 */
public interface ForwardingDispatcher {

    /**
     * Accept a forwarding envelope into the outbox and return the synchronous
     * ack receipt. Dispatch / delivery orchestration is deferred to Stage 8;
     * Stage 7 only confirms durable enqueue via the outbox port.
     */
    ForwardingReceipt dispatch(ForwardingEnvelope envelope, long nowMillisEpoch);
}
