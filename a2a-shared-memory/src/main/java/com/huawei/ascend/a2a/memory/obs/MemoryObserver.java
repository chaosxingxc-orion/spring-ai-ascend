package com.huawei.ascend.a2a.memory.obs;

/**
 * Observability hook for A2A shared-memory — every memory operation and every degraded
 * outcome is reported here, so metrics / structured logs can be emitted without
 * coupling the kits to a backend. Default is a no-op. Implementations MUST be
 * fault-isolated (never let observability break the memory path).
 */
public interface MemoryObserver {

    /**
     * A memory operation completed.
     *
     * @param op        operation name, e.g. {@code recall} / {@code remember} /
     *                  {@code forget} / {@code shared.put} / {@code shared.get}
     * @param scope     low-cardinality scope label (tenant, NOT user id) for runtime metrics
     * @param ok        whether it succeeded
     * @param latencyMs wall-clock latency
     */
    void onOperation(String op, String scope, boolean ok, long latencyMs);

    /**
     * An operation degraded — fail-open, circuit-open, or an ownership rejection.
     *
     * @param reason coarse reason, e.g. {@code circuit-open} / {@code backend-error} /
     *               {@code ownership-rejected}
     */
    void onDegraded(String op, String scope, String reason);

    MemoryObserver NOOP = new MemoryObserver() {
        @Override
        public void onOperation(String op, String scope, boolean ok, long latencyMs) {
        }

        @Override
        public void onDegraded(String op, String scope, String reason) {
        }
    };
}
