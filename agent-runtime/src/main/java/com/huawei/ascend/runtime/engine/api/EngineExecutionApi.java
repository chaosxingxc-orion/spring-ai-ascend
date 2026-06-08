package com.huawei.ascend.runtime.engine.api;

/**
 * Engine dispatch API.
 *
 * <p>The control layer calls this API to enqueue execution, resume, and cancel
 * commands. The engine accepts or rejects enqueue only; execution progress is
 * reported later through the control callback port.
 */
public interface EngineExecutionApi {

    /**
     * Enqueue an Agent execution request.
     *
     * @param request execution request containing scope and input.
     * @return enqueue status (SUCCESS if accepted, FAILED if not accepted).
     */
    EnqueueEngineStatus enqueueExecution(EnqueueEngineExecutionRequest request);

    /**
     * Enqueue a resume request for an interrupted Agent execution.
     *
     * @param request resume request containing scope and input.
     * @return enqueue status (SUCCESS if accepted, FAILED if not accepted).
     */
    EnqueueEngineStatus enqueueResume(EnqueueEngineResumeRequest request);

    /**
     * Enqueue a cancel request for an Agent execution.
     *
     * @param request cancel request containing scope.
     * @return enqueue status (SUCCESS if accepted, FAILED if not accepted).
     */
    EnqueueEngineStatus enqueueCancel(EnqueueEngineCancelRequest request);
}
