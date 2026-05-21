package com.huawei.ascend.service.engine.spi;

import java.util.List;
import java.util.Map;

/**
 * Engine-produced compute result.
 *
 * <p>Returned by {@link StatelessEngine#execute(AgentInvokeRequest)}.
 * The orchestrator merges the delta into Run + Task + Session state.
 *
 * <p>{@code runStatusTransition} hints the requested Run state
 * transition. {@code SUSPENDED} happens only via the checked-exception
 * flow ({@code SuspendSignal}); {@code YIELDED} uses the ON_YIELD hook
 * + this hint (cooperative scheduling, no state-machine transition).
 *
 * @param runStatusTransition  requested transition hint (no_change | succeeded | failed | suspended | yielded).
 * @param taskStateDelta       patches to Task control state.
 * @param sessionStateDelta    patches to Session context.
 * @param memoryWriteIntents   memory write operations (routed through GraphMemoryRepository).
 * @param metrics              engine-reported metrics (tokens, tool calls, ...).
 */
public record StateDelta(
        String runStatusTransition,
        Map<String, Object> taskStateDelta,
        Map<String, Object> sessionStateDelta,
        List<Map<String, Object>> memoryWriteIntents,
        Map<String, Object> metrics) {
}
