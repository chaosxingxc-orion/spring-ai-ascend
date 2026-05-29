package com.huawei.ascend.bus.spi.engine;

import java.io.Serializable;

/**
 * Event emitted on the {@link EnginePort#execute} stream. This wave models the three TERMINAL
 * kinds from {@code docs/contracts/engine-port.v1.yaml#operations.execute.response.terminal_kinds};
 * progress kinds (TOKEN/STEP/...) in {@code agent-event.v1.yaml} are deferred. Exactly one
 * terminal event is emitted per execution leg, and it is the last element.
 *
 * <p>{@code Failed.outcomeHandle} and {@code InterruptRequest.correlationHandle} are opaque
 * keys into the in-JVM {@code EngineOutcomeChannel}: the neutral event carries only the handle
 * string; the in-process / in-JVM-mock driver retrieves the rich thrown object by handle and
 * rethrows it. A real cross-process transport reconstructs failure from {@code errorClass} and
 * resumes via the checkpoint-token protocol instead.
 */
public sealed interface AgentEvent extends Serializable
        permits AgentEvent.Finished, AgentEvent.Failed, AgentEvent.InterruptRequest {

    String runId();

    Kind kind();

    boolean terminal();

    enum Kind { FINISHED, FAILED, INTERRUPT_REQUEST }

    record Finished(String runId, Object result) implements AgentEvent {
        @Override public Kind kind() { return Kind.FINISHED; }
        @Override public boolean terminal() { return true; }
    }

    record Failed(String runId, String errorClass, String message, String outcomeHandle) implements AgentEvent {
        @Override public Kind kind() { return Kind.FAILED; }
        @Override public boolean terminal() { return true; }
    }

    record InterruptRequest(String runId, String checkpointRef, String reason, String correlationHandle)
            implements AgentEvent {
        @Override public Kind kind() { return Kind.INTERRUPT_REQUEST; }
        @Override public boolean terminal() { return true; }
    }
}
