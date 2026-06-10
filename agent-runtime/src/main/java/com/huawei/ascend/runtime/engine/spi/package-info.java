/**
 * Engine provider SPI surface.
 *
 * <p>This package is intentionally small: {@code AgentRuntimeHandler} executes
 * one business Agent, and {@code AgentCardProvider} supplies its public A2A
 * metadata. A concrete handler may implement both interfaces directly, but
 * normal execution code should keep framework-specific decoration inside the
 * framework adapter. {@code SetState} and {@code MemoryProvider} are reserved
 * narrow SPIs for frameworks that need explicit state writes or memory lookup.
 * Frameworks with native checkpointing can use their own checkpointer
 * configuration without going through these optional surfaces.
 * Engine inbound calls live in {@link com.huawei.ascend.runtime.engine.api};
 * the engine internal command runtime ({@code EngineCommand*},
 * {@code EngineWorker}) and the engine outbound clients to access/task-control
 * ({@code TaskControlClient}, {@code AccessLayerClient}) both live in the
 * engine root package {@link com.huawei.ascend.runtime.engine}.
 */
package com.huawei.ascend.runtime.engine.spi;
