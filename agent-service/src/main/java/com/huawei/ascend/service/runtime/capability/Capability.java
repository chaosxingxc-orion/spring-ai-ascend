package com.huawei.ascend.service.runtime.capability;

import com.huawei.ascend.engine.orchestration.spi.ExecutorDefinition;

/**
 * A named, runnable unit of work that the run API can dispatch.
 *
 * <p>A {@code Capability} maps a {@code capabilityName} (the value carried in
 * {@code POST /v1/runs}) to an {@link ExecutorDefinition} the orchestrator
 * knows how to execute. Business modules contribute capabilities as Spring
 * beans without patching the platform — the only extension point a
 * configuration-driven agent author needs to reach first execution.
 *
 * <p>The platform itself ships no business capabilities; a deployment that
 * exposes none simply has an empty {@link CapabilityRegistry} and every
 * {@code POST /v1/runs} fails fast with {@code unknown_capability}.
 */
public interface Capability {

    /** Stable identifier matched against {@code CreateRunRequest.capabilityName}. */
    String capabilityName();

    /** The executor definition the orchestrator runs for this capability. */
    ExecutorDefinition definition();

    /**
     * Optional seed payload handed to the executor's first step. Defaults to
     * {@code null} (the executor starts from its own initial state).
     */
    default Object initialPayload() {
        return null;
    }
}
