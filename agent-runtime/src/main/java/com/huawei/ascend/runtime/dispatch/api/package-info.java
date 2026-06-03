/**
 * Engine inbound API surface.
 *
 * <p>API (provided/inbound) interfaces are implemented by the engine and called
 * by external services. Contains the
 * {@link com.huawei.ascend.service.engine.api.EngineDispatchApi} async dispatch
 * entry point for task-centric-control to enqueue Agent execution requests. The
 * interface is the contract;
 * {@link com.huawei.ascend.service.engine.api.EnqueueEngineExecutionRequest},
 * {@link com.huawei.ascend.service.engine.api.EnqueueEngineResumeRequest},
 * {@link com.huawei.ascend.service.engine.api.EnqueueEngineCancelRequest}, and
 * {@link com.huawei.ascend.service.engine.api.EnqueueEngineStatus} are the carrier types.
 *
 * <p>API vs SPI (directional definition): API interfaces are inbound — the engine
 * implements them, external callers invoke them. SPI interfaces are outbound or
 * extension points — the engine defines them, external services or plugins
 * implement them (see {@link com.huawei.ascend.service.engine.spi}).
 *
 * <p>Design authority:
 * {@code 2026-05-30-l1--agent-service-engine-model-design.md}.
 *
 * <p>Imports only {@code java.*} and
 * {@code com.huawei.ascend.service.engine.model} (execution scope and input carriers).
 */
package com.huawei.ascend.service.engine.api;
