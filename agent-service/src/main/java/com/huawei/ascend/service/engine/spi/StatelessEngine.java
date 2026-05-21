package com.huawei.ascend.service.engine.spi;

/**
 * Stateless engine SPI per ADR-0100 (rc22) agent-service runtime-role decomposition.
 *
 * <p>Pure-function compute boundary: the engine receives the projected
 * task metadata + injected context and returns a state delta. The engine
 * MUST NOT hold persistent state; the Reactive Orchestrator owns the
 * Read-Modify-Write closure.
 *
 * <p>Authority: ADR-0100. Wire contract:
 * {@code docs/contracts/agent-invoke-request.v1.yaml} (status:
 * design_only at rc22; runtime impl in rc24).
 *
 * <p>Coexistence with {@code com.huawei.ascend.engine.spi.ExecutorAdapter}:
 * per ADR-0100, the relationship between {@code StatelessEngine} and the
 * existing {@code ExecutorAdapter} is reconciled in rc23 (sibling SPIs
 * vs single-interface decision documented in ADR-0100 §non_goals).
 *
 * <p>Yield + SuspendSignal coexistence (per ADR-0100):
 * <ul>
 *   <li>{@link com.huawei.ascend.engine.orchestration.spi.SuspendSignal}
 *       (checked exception) remains canonical for state-machine
 *       suspension.</li>
 *   <li>{@code HookPoint.ON_YIELD} (added to engine-hooks.v1.yaml in
 *       rc22) is the cooperative-scheduling hint when the engine asks
 *       to be rescheduled without a state-machine transition.</li>
 * </ul>
 */
public interface StatelessEngine {

    /**
     * Pure-function execution: compute a state delta from task metadata
     * and the projected context. Engine MUST NOT mutate persistent
     * state; the orchestrator merges the returned delta back into Run +
     * Task + Session.
     *
     * @param request the AgentInvokeRequest carrying RunID + SessionContext
     *                + InjectedSkills + task metadata.
     * @return the StateDelta describing requested transitions + writes.
     */
    StateDelta execute(AgentInvokeRequest request);
}
