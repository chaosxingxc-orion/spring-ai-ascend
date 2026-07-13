package com.huawei.ascend.bus.forwarding.spi;

/**
 * Gateway-side observed status of a client / A2A invocation (FEAT-013 §2.3.3,
 * shared by FEAT-014 for the remote-Task viewpoint). This is the
 * <em>invocation-semantics</em> layer observed by the gateway / caller; it is
 * orthogonal to the bus-delivery layer {@link ForwardingStatus}
 * (PENDING / DISPATCHING / ACKED / RETRY_SCHEDULED / DLQ / EXPIRED).
 *
 * <p>{@link #UNKNOWN} is the accept-window unknown state — the gateway could
 * not confirm within the accept window whether the server built a Task. It is
 * neither success nor failure; the client retries with the same
 * {@code idempotencyKey} (FEAT-013 §2.3.4 / §4.3, RFC 2119 MUST).
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §2.3.3};
 * {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-014-a2a-call-event-forwarding.md §2.3.2}.
 */
// scope: forwarding SPI — pure Java invocation observed-status; no payload body
public enum InvocationResponseStatus {
    /** Final A2A response obtained within the blocking window. */
    COMPLETED_RESPONSE,
    /** Server built/reused a Task and returned a taskId, but the final response is not yet in. */
    ACCEPTED_WITH_TASK,
    /** Server Task's A2A SSE stream is ready to subscribe; the gateway can bridge. */
    STREAM_READY,
    /** Server explicitly rejected; no Task was created. */
    REJECTED,
    /** The invocation event or server-side handling definitively failed. */
    FAILED,
    /** Accept-window unknown: could not confirm whether the server built a Task. */
    UNKNOWN
}
