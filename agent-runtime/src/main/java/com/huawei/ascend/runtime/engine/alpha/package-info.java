/**
 * Alpha engine adapter: hosts the openjiuwen 2.0 Alpha (PEV) execution model behind the
 * framework-neutral {@code engine.spi.AgentRuntimeHandler} SPI. The runtime drives
 * {@code execute}; this package maps the engine's reactive {@code Flux<AgentEvent>}
 * output into the synchronous {@code AgentExecutionResult} stream the SPI expects, with
 * terminal-safety guarantees (first-terminal-wins, no silent end) enforced at the bridge.
 *
 * @see com.huawei.ascend.runtime.engine.alpha.FluxToResultStream
 * @see com.huawei.ascend.runtime.engine.alpha.AlphaRuntimeHandler
 */
package com.huawei.ascend.runtime.engine.alpha;
