/**
 * Engine provider SPI surface.
 *
 * <p>This package is intentionally small and protocol-neutral: it must not
 * reference {@code org.a2aproject} types (enforced by
 * {@code RuntimePackageBoundaryTest}). {@code AgentRuntimeHandler} executes one
 * business Agent against the neutral {@code AgentExecutionContext} /
 * {@code RuntimeMessage} input model. Protocol metadata supply
 * ({@code AgentCardProvider}, {@code AgentCards}) lives in the protocol bridge
 * package {@link com.huawei.ascend.runtime.engine.a2a}. A concrete handler may
 * implement both the handler and card interfaces directly, but normal
 * execution code should keep framework-specific decoration inside the
 * framework adapter. {@code MemoryProvider} is a reserved narrow SPI for
 * frameworks that need runtime-provided memory init/search/save integration.
 * {@code SkillHubProvider} is a reserved narrow SPI for progressive skill
 * discovery/loading; concrete framework adapters decide how to install those
 * skills. {@code McpProvider} is the matching narrow SPI for MCP tool discovery
 * and tool invocation; concrete framework adapters own how those tools are
 * installed. Frameworks with native checkpointing can use their own checkpointer
 * configuration without going through these optional surfaces.
 */
package com.huawei.ascend.runtime.engine.spi;
