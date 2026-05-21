package com.huawei.ascend.service.engine.adapter;

import com.huawei.ascend.service.engine.spi.AgentInvokeRequest;
import com.huawei.ascend.service.engine.spi.StateDelta;
import com.huawei.ascend.service.engine.spi.StatelessEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference in-memory implementation of {@link StatelessEngine} per
 * ADR-0100.
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
        //
        // use HashMap instead of Map.of(...). The runId is
        // already non-null per the AgentInvokeRequest canonical constructor
        //, but defence-in-depth: Map.of's null-value rejection would
        // throw NPE if any future field added here is nullable.
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("engine", "InMemoryStatelessEngine");
        metrics.put("request_id", request.runId());
        return new StateDelta(
                "no_change",
                Map.of(),
                Map.of(),
                List.of(),
                metrics);
    }
}
