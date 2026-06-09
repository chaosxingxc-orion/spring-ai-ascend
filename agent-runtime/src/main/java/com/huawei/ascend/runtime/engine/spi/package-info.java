/**
 * Engine provider SPI surface.
 *
 * <p>This package is intentionally small: {@code AgentRuntimeHandler} is the
 * only interface that external agent providers implement. Providers can attach
 * optional {@code AgentRuntimeProvider} capabilities through the base handler.
 * {@code StateProvider} is the first provider specialization and translates the
 * neutral Agent State map into framework-native session state.
 * Engine inbound calls live in {@link com.huawei.ascend.runtime.engine.api};
 * the engine internal command runtime ({@code EngineCommand*},
 * {@code EngineWorker}) and the engine outbound clients to access/task-control
 * ({@code TaskControlClient}, {@code AccessLayerClient}) both live in the
 * engine root package {@link com.huawei.ascend.runtime.engine}.
 */
package com.huawei.ascend.runtime.engine.spi;
