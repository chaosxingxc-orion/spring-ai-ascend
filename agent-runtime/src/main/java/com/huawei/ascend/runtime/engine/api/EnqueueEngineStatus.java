package com.huawei.ascend.runtime.engine.api;

/**
 * Enqueue status returned by {@link EngineExecutionApi}.
 *
 * <p>Indicates whether the engine accepted the enqueue request.
 * Does not represent the real execution status; real execution status
 * is written back through the outbound port
 * {@code com.huawei.ascend.runtime.engine.TaskControlClient}.
 */
public enum EnqueueEngineStatus {
    /**
     * Engine has accepted the enqueue request.
     */
    SUCCESS,

    /**
     * Engine did not accept the enqueue request.
     */
    FAILED
}
