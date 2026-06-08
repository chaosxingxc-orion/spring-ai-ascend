/**
 * Engine provider SPI surface.
 *
 * <p>This package is intentionally small: {@code AgentRuntimeHandler} is the only
 * interface that external agent providers implement. Engine inbound calls live
 * in {@link com.huawei.ascend.runtime.engine.api}; the engine internal command
 * runtime ({@code EngineCommand*}, {@code EngineWorker}) and the engine outbound
 * clients to access/task-control ({@code TaskControlClient}, {@code AccessLayerClient})
 * both live in the engine root package {@link com.huawei.ascend.runtime.engine}.
 */
package com.huawei.ascend.runtime.engine.spi;
