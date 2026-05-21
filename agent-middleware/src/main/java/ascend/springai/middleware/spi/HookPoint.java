package ascend.springai.middleware.spi;

/**
 * Canonical hook points an execution engine may expose to the Runtime so
 * cross-cutting policies (model gateway selection, tool authorization, memory
 * governance, tenant policy, quota, observability, sandbox routing,
 * checkpoint, failure handling) attach without engine code knowing about
 * concrete middleware implementations.
 *
 * <p>Order matches the {@code hooks:} list in
 * {@code docs/contracts/engine-hooks.v1.yaml}. Gate Rule 57 cross-checks the
 * two sides so the schema and the enum never drift.
 *
 * <p>W2.x Phase 2 wires only three hooks at the orchestrator level
 * ({@link #ON_ERROR}, {@link #BEFORE_SUSPENSION}, {@link #BEFORE_RESUME}) —
 * see {@code engine-hooks.v1.yaml#phase_2_mandatory_hooks_fired_by_orchestrator}.
 * The remaining six require engine-side firing and land in W2 Telemetry
 * Vertical.
 *
 * <p>Pure Java — no Spring imports per architecture §4.7
 * (orchestration.spi imports only java.*).
 *
 * <p>Authority: ADR-0073; CLAUDE.md Rule 45.
 */
public enum HookPoint {
    BEFORE_LLM_INVOCATION,
    AFTER_LLM_INVOCATION,
    BEFORE_TOOL_INVOCATION,
    AFTER_TOOL_INVOCATION,
    BEFORE_MEMORY_READ,
    AFTER_MEMORY_WRITE,
    BEFORE_SUSPENSION,
    BEFORE_RESUME,
    ON_ERROR,
    /**
     * Cooperative-scheduling hint per ADR-0100 Yield/SuspendSignal coexistence.
     * Engine asks orchestrator to be rescheduled WITHOUT a state-machine
     * transition; distinct from {@link com.huawei.ascend.engine.orchestration.spi.SuspendSignal}
     * which remains canonical for state-machine suspension.
     */
    ON_YIELD
}
