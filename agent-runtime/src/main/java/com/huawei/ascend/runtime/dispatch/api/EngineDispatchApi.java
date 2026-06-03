package com.huawei.ascend.service.engine.api;

/**
 * Engine dispatch API.
 *
 * <p>The sole inbound entry point for task-centric-control to call the engine.
 * Implemented by the engine, called by task-centric-control. Only responsible
 * for async enqueuing. Does not directly execute Agents. Does not directly
 * return real execution status. Real execution status is written back through
 * the outbound port {@code com.huawei.ascend.service.engine.port.TaskControlClient}.
 *
 * <p>This is an API (provided/inbound interface), not an SPI: the engine
 * implements it and external callers invoke it. See the engine model design
 * (§2.1 directional definition, §4 API definition) for the API/SPI taxonomy.
 *
 * <p>Design authority: {@code 2026-05-30-l1--agent-service-engine-model-design.md}.
 */
public interface EngineDispatchApi {

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
