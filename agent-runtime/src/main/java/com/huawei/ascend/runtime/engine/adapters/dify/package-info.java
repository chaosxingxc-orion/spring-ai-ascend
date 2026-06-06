/**
 * Dify remote-protocol adapter: a {@link com.huawei.ascend.runtime.engine.spi.AgentDriver} that
 * fronts an existing Dify application over REST + SSE, reusing its workflows / tools / memory as-is.
 * Demonstrates the second adapter shape (remote protocol) alongside the in-process framework
 * adapters; the runtime core is unchanged.
 *
 * <p>Authority: ADR-0160 (neutral execution core).
 */
package com.huawei.ascend.runtime.engine.adapters.dify;
