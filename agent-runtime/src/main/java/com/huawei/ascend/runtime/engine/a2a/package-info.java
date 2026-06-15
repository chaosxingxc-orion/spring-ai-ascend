/**
 * A2A access layer: bridges the A2A SDK server surface to the framework-neutral
 * {@code engine.spi.AgentRuntimeHandler} SPI — task lifecycle, result routing,
 * per-invocation trajectory wiring, and remote A2A tool orchestration. All
 * collaborators run synchronously on the execute thread; the single-writer
 * {@code AgentEmitter} is passed down, never stored.
 *
 * <p><b>Logging convention.</b> Operator logs in this package never print free
 * business text (input, output, prompts, error payloads) verbatim: log either the
 * length ({@code textChars}, {@code outputChars}, {@code promptChars}) or text passed
 * through {@link com.huawei.ascend.runtime.engine.a2a.A2aLogMasking} — the same
 * masking decision the trajectory channel applies, because the log collection chain
 * is an export channel too. Correlation rides on the executor-scoped MDC keys
 * ({@code contextId}/{@code taskId}/{@code tenantId}/{@code agentId}); trajectory
 * sink logs additionally inline {@code taskId} because framework worker threads do
 * not inherit the executor's MDC.
 */
package com.huawei.ascend.runtime.engine.a2a;
