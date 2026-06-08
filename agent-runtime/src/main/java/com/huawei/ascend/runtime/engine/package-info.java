/**
 * Agent execution scheduling and concrete agent-framework adaptation.
 *
 * <p>This layer pulls the {@link
 * com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler} for a command, runs
 * it on a worker, and reports every outcome — lifecycle, streaming output, and
 * terminal states — as a single {@link
 * com.huawei.ascend.runtime.engine.EngineEvent} through ONE outbound seam,
 * {@link com.huawei.ascend.runtime.engine.TaskControlClient}. The control plane
 * is the sole authority and fans caller-facing egress out from there, so the
 * engine never writes authority and output twice. The engine root holds that
 * core (dispatcher, worker, command/event model, execution scope/input/output,
 * the handler registry); the framework-neutral boundary sub-packages are
 * {@code api} (inbound, called by control), {@code spi} (extension points
 * implemented by frameworks/users), and {@code openjiuwen} (the first framework
 * adapter).
 *
 * <p>Dependency rule: the engine core depends only on {@code common},
 * {@code queue} and the JDK — it must not depend on Spring Boot or the A2A SDK.
 * Only {@code engine.openjiuwen} may depend on the openJiuwen framework.
 */
package com.huawei.ascend.runtime.engine;
