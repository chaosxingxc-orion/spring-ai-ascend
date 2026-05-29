package com.huawei.ascend.bus.spi.engine;

/**
 * Service-side execution context: the {@link ExecutionContext} subtype that adds tenant /
 * session ownership and the child-run suspend capability. The Service populates this and
 * passes it to node functions; tenant/session live HERE, not on the neutral
 * {@link ExecutionContext} that crosses the EnginePort boundary (ADR-0158). In-process the
 * engine receives this concrete subtype by reference; across a transport the engine sees only
 * the neutral {@link ExecutionContext} surface.
 *
 * <p>Pure Java — no Spring imports per architecture §4.7.
 *
 * <p>The single nesting entry-point is {@link #suspendForChild}: it suspends the current run,
 * starts a child run under childMode, and returns the child's final result. From the caller's
 * view it is synchronous; internally it throws {@link SuspendSignal}.
 */
public interface RunContext extends ExecutionContext {

    String tenantId();

    /**
     * Optional logical session identifier (ADR-0062 — Trace &#8596; Run &#8596; Session N:M).
     * MAY be null at L1.x; non-null in posture=research/prod from W2.
     */
    String sessionId();

    /**
     * Trace correlation carrier. L1.x default implementation is a no-op carrier; W2 wires an
     * OTel-backed implementation.
     */
    TraceContext traceContext();

    /**
     * Request suspension of the current run and delegation to a child executor.
     *
     * @throws SuspendSignal always — caught only by the Orchestrator
     */
    Object suspendForChild(String parentNodeKey, RunMode childMode,
                           ExecutorDefinition childDef, Object resumePayload)
            throws SuspendSignal;
}
