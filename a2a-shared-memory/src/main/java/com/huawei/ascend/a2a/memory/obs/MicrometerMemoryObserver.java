package com.huawei.ascend.a2a.memory.obs;

import io.micrometer.core.instrument.Metrics;
import java.time.Duration;

/**
 * Emits A2A shared-memory metrics via Micrometer's global registry (surfaces at
 * {@code /actuator/prometheus} when a registry is bound; harmless no-op in the
 * offline eval). Low-cardinality tags only — op / outcome / reason / scope(tenant).
 *
 * <ul>
 *   <li>{@code a2amem.ops{op,outcome}} — operation count</li>
 *   <li>{@code a2amem.op.latency{op}} — operation latency</li>
 *   <li>{@code a2amem.degraded{op,reason}} — degraded outcomes</li>
 * </ul>
 */
public final class MicrometerMemoryObserver implements MemoryObserver {

    @Override
    public void onOperation(String op, String scope, boolean ok, long latencyMs) {
        Metrics.counter("a2amem.ops", "op", op, "outcome", ok ? "ok" : "error").increment();
        Metrics.timer("a2amem.op.latency", "op", op).record(Duration.ofMillis(latencyMs));
    }

    @Override
    public void onDegraded(String op, String scope, String reason) {
        Metrics.counter("a2amem.degraded", "op", op, "reason", reason).increment();
    }
}
