package com.huawei.ascend.bus.spi.engine;

import java.io.Serializable;

/**
 * Event emitted on the {@link EnginePort#execute} stream. This wave models the three TERMINAL
 * kinds from {@code docs/contracts/engine-port.v1.yaml#operations.execute.response.terminal_kinds};
 * progress kinds (TOKEN/STEP/...) in {@code agent-event.v1.yaml} are deferred. Exactly one
 * terminal event is emitted per execution leg, and it is the last element.
 *
 * <p>{@code Failed.outcomeHandle} and {@code InterruptRequest.correlationHandle} are opaque
 * keys into an in-JVM outcome channel (a design target — no implementation ships in this
 * repository yet): the neutral event carries only the handle string; an in-process driver
 * retrieves the rich thrown object by handle and rethrows it. A real cross-process transport
 * MUST ignore the handles, reconstruct failure from {@code errorClass}, and resume via the
 * checkpoint-token protocol instead.
 */
public sealed interface AgentEvent extends Serializable
        permits AgentEvent.Finished, AgentEvent.Failed, AgentEvent.InterruptRequest {

    String runId();

    Kind kind();

    boolean terminal();

    enum Kind { FINISHED, FAILED, INTERRUPT_REQUEST }

    record Finished(String runId, Object result) implements AgentEvent {
        public Finished {
            runId = requireNonBlank(runId, "runId");
        }

        @Override public Kind kind() { return Kind.FINISHED; }
        @Override public boolean terminal() { return true; }
    }

    record Failed(String runId, String errorClass, String message, String outcomeHandle) implements AgentEvent {
        public Failed {
            runId = requireNonBlank(runId, "runId");
            errorClass = requireNonBlank(errorClass, "errorClass");
        }

        @Override public Kind kind() { return Kind.FAILED; }
        @Override public boolean terminal() { return true; }
    }

    record InterruptRequest(String runId, String checkpointRef, String reason, String correlationHandle)
            implements AgentEvent {
        public InterruptRequest {
            runId = requireNonBlank(runId, "runId");
            // The resume token of the checkpoint protocol — an interrupt without it is unresumable.
            checkpointRef = requireNonBlank(checkpointRef, "checkpointRef");
        }

        @Override public Kind kind() { return Kind.INTERRUPT_REQUEST; }
        @Override public boolean terminal() { return true; }
    }

    private static String requireNonBlank(String value, String name) {
        java.util.Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
