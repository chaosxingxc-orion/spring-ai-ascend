package com.huawei.ascend.service.engine.api;

/**
 * Enqueue status returned by {@link EngineDispatchApi}.
 *
 * <p>Indicates whether the engine accepted the enqueue request.
 * Does not represent the real execution status; real execution status
 * is written back through the outbound port
 * {@code com.huawei.ascend.service.engine.port.TaskControlClient}.
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
