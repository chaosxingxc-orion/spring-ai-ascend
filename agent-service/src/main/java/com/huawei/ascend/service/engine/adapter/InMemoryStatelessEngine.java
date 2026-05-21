package com.huawei.ascend.service.engine.adapter;

import com.huawei.ascend.service.engine.spi.AgentInvokeRequest;
import com.huawei.ascend.service.engine.spi.StateDelta;
import com.huawei.ascend.service.engine.spi.StatelessEngine;

import java.util.List;
import java.util.Map;

/**
 * Reference in-memory implementation of {@link StatelessEngine} per
 * ADR-0100 (rc24).
 *
 * <p>Posture-gated for dev/research; production wiring lands when the
 * actual Workflow / ReAct engine adapters land via
 * {@code com.huawei.ascend.engine.spi.ExecutorAdapter} consumers.
 *
 * <p>This reference impl:
 * <ul>
 *   <li>Accepts {@link AgentInvokeRequest} and returns a no-op
 *       {@link StateDelta} (status: no_change).</li>
 *   <li>Demonstrates the pure-function contract: no state mutation; no
 *       I/O; deterministic output for a given input.</li>
 *   <li>Is used by integration tests as a stand-in for real engines.</li>
 * </ul>
 *
 * <p>The Reactive Orchestrator
 * ({@code com.huawei.ascend.service.orchestrator}) is responsible for
 * merging the returned delta back into Run + Task + Session state.
 */
public class InMemoryStatelessEngine implements StatelessEngine {

    @Override
    public StateDelta execute(AgentInvokeRequest request) {
        // Reference impl: no-op delta. Real engines plug in via the
        // service.engine.adapter sub-package as adapters over
        // ExecutorAdapter (from agent-execution-engine).
        return new StateDelta(
                "no_change",
                Map.of(),
                Map.of(),
                List.of(),
                Map.of("engine", "InMemoryStatelessEngine", "request_id", request.runId()));
    }
}
