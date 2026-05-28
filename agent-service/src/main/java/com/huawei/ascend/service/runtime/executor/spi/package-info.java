/**
 * Executor Adapter SPI for the agent-service runtime layer.
 *
 * <p>Authority: ADR-0155 (AgentService L1 v1.2 internal module design absorption).
 * Status at this commit: design_only — interface declared, no production
 * implementation. Reference impls land in a follow-up impl-mode wave.</p>
 *
 * <p>The {@link com.huawei.ascend.service.runtime.executor.spi.ExecutorAdapter}
 * SPI unifies three Agent forms — Native (in-process platform-bean DI),
 * Third-party (in-process framework-bridge replacement), and Remote
 * (out-of-process A2A protocol client) — behind a single execute contract.
 * {@link com.huawei.ascend.service.runtime.executor.spi.InjectionMode}
 * captures the wiring choice an adapter declares at registration time.</p>
 */
package com.huawei.ascend.service.runtime.executor.spi;
