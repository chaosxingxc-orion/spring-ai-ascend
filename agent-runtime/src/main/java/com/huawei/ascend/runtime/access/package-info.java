/**
 * External ingress and the caller-visible output channel. The current (and only)
 * implemented protocol is A2A.
 *
 * <p>What this layer does: translate an inbound A2A JSON-RPC call into a neutral
 * {@link com.huawei.ascend.runtime.common.AgentRequest}, maintain one output
 * channel per request, drive that channel from the responses control fans back,
 * and call the control API to submit / resume / cancel a task.
 *
 * <p>What it deliberately does not do: it never executes an agent, never advances
 * task state, never touches the engine's internal queue, and never persists an
 * agent session. Those belong to {@code control}, {@code engine} and
 * {@code session} respectively.
 *
 * <p>Sub-packages mark external boundaries only: {@code a2a} (the A2A protocol
 * adapter — the sole place allowed to depend on the A2A SDK and an HTTP stack),
 * {@code api} (the inbound call surface other layers use), and {@code output}
 * (caller-facing egress channels). Dependency rule: access may depend on
 * {@code common}, {@code session.api}, {@code control.api}, {@code queue} and
 * {@code engine} — it implements the engine-defined outbound
 * {@link com.huawei.ascend.runtime.engine.AccessLayerClient} port (carrying
 * {@code EngineEvent}/{@code EngineExecutionScope}/{@code EngineOutput} across it)
 * and references {@code engine.spi.AbstractAgentRuntimeHandler} when building the
 * agent card. It must not depend on {@code engine.openjiuwen} (the framework
 * adapter) — that is the agent framework, and the dependency is guarded by
 * RuntimePackageBoundaryTest.
 */
package com.huawei.ascend.runtime.access;
