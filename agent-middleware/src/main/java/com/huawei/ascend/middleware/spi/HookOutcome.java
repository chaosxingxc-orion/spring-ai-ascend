package com.huawei.ascend.middleware.spi;

/**
 * Result of a {@link RuntimeMiddleware} hook invocation. The dispatcher
 * aggregates outcomes across registered middlewares and decides the next
 * step.
 *
 * <p>Pure Java — no Spring imports per architecture §4.7
 * (orchestration.spi imports only java.*).
 *
 * <p>Authority: ADR-0073.
 */
public sealed interface HookOutcome
        permits HookOutcome.Proceed, HookOutcome.ShortCircuit, HookOutcome.Fail {

    /** The middleware permits dispatch to continue unchanged. */
    record Proceed() implements HookOutcome {
        private static final Proceed INSTANCE = new Proceed();
        public static Proceed instance() { return INSTANCE; }
    }

    /**
     * <b>Status:</b> the dispatcher returns this outcome but the
     * SyncOrchestrator does NOT consume it; engine-bypass is logged only.
     *
     * <p>TARGET behavior (Rule 45.b, W2 Telemetry Vertical): a middleware
     * returns this to satisfy the request without running the engine — used by
     * cache layers, dry-run middlewares, or replay middlewares. The dispatcher
     * would return {@code result} as the run output.
     */
    record ShortCircuit(Object result) implements HookOutcome {}

    /**
     * <b>Status:</b> the dispatcher returns this outcome but the
     * SyncOrchestrator does NOT consume it; Run-state transition is NOT applied.
     *
     * <p>TARGET behavior (Rule 45.b, W2 Telemetry Vertical): the middleware
     * rejects dispatch and the Run transitions to FAILED with {@code reason}
     * as the rejection cause.
     */
    record Fail(String reason) implements HookOutcome {}

    static HookOutcome proceed() {
        return Proceed.instance();
    }
}
