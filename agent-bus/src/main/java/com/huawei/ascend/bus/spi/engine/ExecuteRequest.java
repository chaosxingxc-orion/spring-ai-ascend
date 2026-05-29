package com.huawei.ascend.bus.spi.engine;

import java.io.Serializable;
import java.util.Objects;

/**
 * Neutral wire request for {@link EnginePort#execute}. Carries a serializable
 * {@link DefinitionRef} (never an inline {@code ExecutorDefinition} with JVM lambdas), the
 * opaque input payload, optional resume checkpoint reference, and W3C traceparent. Mirrors
 * {@code docs/contracts/engine-port.v1.yaml#operations.execute.request}.
 */
public record ExecuteRequest(
        String runId,
        String engineType,
        DefinitionRef definitionRef,
        Object input,
        String startCheckpointRef,
        String traceparent,
        String identityRef
) implements Serializable {

    public ExecuteRequest {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required");
        }
        if (engineType == null || engineType.isBlank()) {
            throw new IllegalArgumentException("engineType is required");
        }
        Objects.requireNonNull(definitionRef, "definitionRef is required");
        if (traceparent == null || traceparent.isBlank()) {
            throw new IllegalArgumentException("traceparent is required");
        }
    }
}
