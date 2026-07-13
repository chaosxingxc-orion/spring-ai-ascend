package com.huawei.ascend.bus.forwarding.spi;

/**
 * Event-type discriminator carried on every {@link ForwardingEnvelope}
 * (FEAT-013 + FEAT-014). It lets the gateway, event-bus (治理中继), and
 * agent-runtime route / fan-out / merge invocation and A2A-call events without
 * unpacking the A2A payload.
 *
 * <p>Two families, four directions:
 * <ul>
 *   <li><b>FEAT-013 — client invocation</b>: {@code CLIENT_INVOCATION_*}
 *       (client → server, via gateway → event-bus → agent-runtime) and
 *       {@code INVOCATION_*} (server → gateway, via agent-runtime → event-bus
 *       → gateway).</li>
 *   <li><b>FEAT-014 — service-to-service A2A call</b>:
 *       {@code A2A_CALL_*} / {@code A2A_STREAM_*} (source runtime ↔ target
 *       runtime, via event-bus).</li>
 * </ul>
 *
 * <p>{@code INVOCATION_ACCEPTED} / {@code A2A_CALL_ACCEPTED} and
 * {@code INVOCATION_STREAM_READY} / {@code A2A_STREAM_READY} are deliberately
 * separate — accepting a Task and a stream becoming ready are distinct
 * observable events (FEAT-013 §2.3.4 / FEAT-014 §2.3.2, RFC 2119 MUST).
 *
 * <p>Forbidden-payload invariant (HD4): no event type carries a token chunk,
 * SSE frame, payload body, or Task execution state in the broker message
 * body; the discriminator is control-plane only.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §2.3.2};
 * {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-014-a2a-call-event-forwarding.md §2.3.1}.
 */
// scope: forwarding SPI — pure Java event-type discriminator; no payload body
public enum AgentBusEventType {
    // ---- FEAT-013: client -> server (gateway -> event-bus -> agent-runtime) ----
    /** Client initiates an Agent call / Task creation. */
    CLIENT_INVOCATION_REQUESTED,
    /** Client cancels an existing Task (→ A2A CancelTask). */
    CLIENT_INVOCATION_CANCEL_REQUESTED,
    /** Client queries a Task / ListTasks (→ A2A GetTask / ListTasks). */
    CLIENT_INVOCATION_QUERY_REQUESTED,
    /** Client subscribes to an existing Task's A2A SSE (based on taskId). */
    CLIENT_STREAM_SUBSCRIBE_REQUESTED,
    // ---- FEAT-013: server -> gateway (agent-runtime -> event-bus -> gateway) ----
    /** Server accepted and created/reused a Task (carries taskId). */
    INVOCATION_ACCEPTED,
    /** Server explicitly rejected; no Task was created. */
    INVOCATION_REJECTED,
    /** Definitive failure (error code + retry semantics). */
    INVOCATION_FAILED,
    /** One-shot A2A response within the blocking window. */
    INVOCATION_RESPONSE,
    /** Task's A2A SSE stream is ready to subscribe (carries stream reference). */
    INVOCATION_STREAM_READY,
    /** Task terminal state (completed/failed/cancelled); carries no token stream. */
    INVOCATION_TERMINAL,
    // ---- FEAT-014: source runtime -> target runtime (caller -> event-bus -> target) ----
    /** Caller initiates a remote A2A call / advances a remote Task (→ A2A message/send). */
    A2A_CALL_REQUESTED,
    /** Caller cancels a remote Task (→ A2A CancelTask). */
    A2A_CALL_CANCEL_REQUESTED,
    /** Caller queries a remote Task (→ A2A GetTask). */
    A2A_CALL_QUERY_REQUESTED,
    /** Caller subscribes to a remote Task's A2A SSE (based on remote taskId). */
    A2A_STREAM_SUBSCRIBE_REQUESTED,
    // ---- FEAT-014: target runtime -> source runtime (target -> event-bus -> caller) ----
    /** Target accepted and created/reused a remote Task (carries remote taskId). */
    A2A_CALL_ACCEPTED,
    /** Target explicitly rejected; no remote Task was created. */
    A2A_CALL_REJECTED,
    /** Definitive remote failure (error code + retry semantics). */
    A2A_CALL_FAILED,
    /** One-shot A2A response within the waiting window. */
    A2A_CALL_RESPONSE,
    /** Remote Task's A2A SSE stream is ready to subscribe (carries stream reference). */
    A2A_STREAM_READY,
    /** Remote Task terminal state (completed/failed/cancelled); carries no token stream. */
    A2A_CALL_TERMINAL
}
