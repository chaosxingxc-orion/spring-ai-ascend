/**
 * Reactive Orchestrator component per ADR-0100 (rc22).
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
 * <p>Implementation lands in rc23 + rc24:
 * <ul>
 *   <li>rc23: Java refactor moves orchestrator logic here from
 *       {@code com.huawei.ascend.service.runtime.orchestration.inmemory}.</li>
 *   <li>rc24: {@code Orchestrator.invoke(AgentInvokeRequest) → Mono<StateDelta>}
 *       reactive wiring + BackpressureRequest channel consumer.</li>
 * </ul>
 *
 * <p>Cross-package boundary (rc23 ArchUnit
 * {@code AgentServiceComponentBoundaryArchTest}):
 * orchestrator → may call engine.adapter, task, session.
 * Reverse direction forbidden.
 *
 * <p>Yield + SuspendSignal coexistence (per ADR-0100):
 * <ul>
 *   <li>{@link com.huawei.ascend.engine.orchestration.spi.SuspendSignal}
 *       checked-exception flow → state-machine suspension.</li>
 *   <li>{@code HookPoint.ON_YIELD} hook → cooperative reschedule
 *       without state-machine transition.</li>
 * </ul>
 */
package com.huawei.ascend.service.orchestrator;
