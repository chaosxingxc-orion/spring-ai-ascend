package com.huawei.ascend.service.engine.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 * @param runStatusTransition  requested transition hint.
 * @param taskStateDelta       patches to Task control state.
 * @param sessionStateDelta    patches to Session context.
 * @param memoryWriteIntents   memory write operations (routed through GraphMemoryRepository).
 * @param metrics              engine-reported metrics (tokens, tool calls, ...).
 */
public record StateDelta(
        RunStatusTransition runStatusTransition,
        Map<String, Object> taskStateDelta,
        Map<String, Object> sessionStateDelta,
        List<Map<String, Object>> memoryWriteIntents,
        Map<String, Object> metrics) {

    public StateDelta {
        Objects.requireNonNull(runStatusTransition, "runStatusTransition");
        Objects.requireNonNull(taskStateDelta, "taskStateDelta");
        Objects.requireNonNull(sessionStateDelta, "sessionStateDelta");
        Objects.requireNonNull(memoryWriteIntents, "memoryWriteIntents");
        Objects.requireNonNull(metrics, "metrics");
        taskStateDelta = Map.copyOf(taskStateDelta);
        sessionStateDelta = Map.copyOf(sessionStateDelta);
        memoryWriteIntents = memoryWriteIntents.stream()
                .map(Map::copyOf)
                .toList();
        metrics = Map.copyOf(metrics);
    }

    public enum RunStatusTransition {
        NO_CHANGE("no_change"),
        SUCCEEDED("succeeded"),
        FAILED("failed"),
        SUSPENDED("suspended"),
        YIELDED("yielded");

        private final String wireValue;

        RunStatusTransition(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }
}
