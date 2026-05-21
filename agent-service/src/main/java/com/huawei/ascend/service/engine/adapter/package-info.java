/**
 * Execution Engine Adapter component.
 *
 * <p>Masks Workflow vs ReAct engine differences; pure-function compute
 * injection. Component #5 of the agent-service 5-component runtime-role
 * decomposition.
 *
 * <p>Implementation lands in 
 * <ul>
 *   <li>{@code InMemoryStatelessEngine} — wraps existing GRAPH +
 *       AGENT_LOOP executors as a {@link com.huawei.ascend.service.engine.spi.StatelessEngine}.</li>
 * </ul>
 *
 * <p>Cross-package boundary (rc23 ArchUnit
 * {@code AgentServiceComponentBoundaryArchTest}):
 * engine.adapter → may call engine.spi.
 * Reverse direction forbidden.
 *
 * <p>Relationship with {@code com.huawei.ascend.engine.spi.ExecutorAdapter}
 * (in the {@code agent-execution-engine} module): the Service-level
 * adapter here CONSUMES the engine-module ExecutorAdapter to materialize
 * StatelessEngine pure-function semantics over the existing dispatch-
 * based executor adapters. Resolved.
 */
package com.huawei.ascend.service.engine.adapter;
