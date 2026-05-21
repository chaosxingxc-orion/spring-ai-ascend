/**
 * Reactive Orchestrator component.
 *
 * <p>Task tempo control, backpressure request handling, A2A protocol
 * envelope packaging. Component #2 of the agent-service 5-component
 * runtime-role decomposition.
 *
 * <p>Owns the Read-Modify-Write closure: invokes
 * {@link com.huawei.ascend.service.engine.spi.StatelessEngine#execute(com.huawei.ascend.service.engine.spi.AgentInvokeRequest)}
 * and merges the returned {@link com.huawei.ascend.service.engine.spi.StateDelta}
 * back into Run + Task + Session state.
 *
 * <p>Implementation lands + 
 * <ul>
 *   <li>Java refactor moves orchestrator logic here from
 *       {@code com.huawei.ascend.service.runtime.orchestration.inmemory}.</li>
 *   <li>{@code Orchestrator.invoke(AgentInvokeRequest) → Mono<StateDelta>}
 *       reactive wiring + BackpressureRequest channel consumer.</li>
 * </ul>
 *
 * <p>Cross-package boundary (rc23 ArchUnit
 * {@code AgentServiceComponentBoundaryArchTest}):
 * orchestrator → may call engine.adapter, task, session.
 * Reverse direction forbidden.
 *
 * <p>Yield + SuspendSignal coexistence ():
 * <ul>
 *   <li>{@link com.huawei.ascend.engine.orchestration.spi.SuspendSignal}
 *       checked-exception flow → state-machine suspension.</li>
 *   <li>{@code HookPoint.ON_YIELD} hook → cooperative reschedule
 *       without state-machine transition.</li>
 * </ul>
 */
package com.huawei.ascend.service.orchestrator;
