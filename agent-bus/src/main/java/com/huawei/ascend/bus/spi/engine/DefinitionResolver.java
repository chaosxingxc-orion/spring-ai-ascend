package com.huawei.ascend.bus.spi.engine;

import java.util.Optional;

/**
 * Bidirectional bridge between the wire-form {@link DefinitionRef} and the runnable
 * {@link ExecutorDefinition}. {@link #resolve} is the ENGINE-facing direction (a remote
 * engine resolves a reference against its own registry); {@link #referenceFor} is the
 * SERVICE-facing direction (the orchestrator obtains the reference for a definition,
 * registering transient/child definitions that have no capability name).
 */
public interface DefinitionResolver {

    Optional<ExecutorDefinition> resolve(DefinitionRef ref);

    DefinitionRef referenceFor(ExecutorDefinition def);
}
