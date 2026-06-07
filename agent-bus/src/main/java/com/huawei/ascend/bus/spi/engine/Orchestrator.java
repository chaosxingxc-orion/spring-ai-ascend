package com.huawei.ascend.bus.spi.engine;

import java.util.UUID;

/**
 * SPI for the top-level orchestration entry point. Pure Java — no Spring imports.
 *
 * The Orchestrator owns:
 *   - the SuspendSignal catch loop
 *   - checkpoint writes (via Checkpointer)
 *   - RunStatus transitions (PENDING → RUNNING → SUSPENDED → RUNNING → SUCCEEDED / FAILED)
 *   - child-run dispatch and result propagation
 *
 * Engine handlers must not catch SuspendSignal. Only the Orchestrator catches it.
 */
public interface Orchestrator {

    /**
     * Start or resume a run.
     *
     * @param runId          identifies the run; must be unique per tenant
     * @param tenantId       owning tenant; required on every call (Rule 11)
     * @param def            what to execute
     * @param initialPayload starting data; ignored on resume (checkpoint is used instead)
     * @return the final result when the run and all its nested children complete
     */
    Object run(UUID runId, String tenantId, ExecutorDefinition def, Object initialPayload);
}
