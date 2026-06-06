/**
 * agent-runtime engine — the framework-neutral execution core.
 *
 * <p>{@link com.huawei.ascend.runtime.engine.RunCoordinator} wraps one
 * {@link com.huawei.ascend.runtime.engine.spi.AgentDriver} and produces the neutral reactive
 * {@link com.huawei.ascend.runtime.common.RunEvent} stream. Per-framework drivers live under
 * {@code engine.adapters.<framework>}; {@code engine.registry.AgentDriverRegistry} resolves a
 * driver by agent id / framework id. The SPI lives under
 * {@link com.huawei.ascend.runtime.engine.spi}.
 *
 * <p>Authority: ADR-0160 (neutral execution core supersedes the dual-mode / EnginePort contract).
 */
package com.huawei.ascend.runtime.engine;
