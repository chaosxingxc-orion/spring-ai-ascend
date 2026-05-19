package ascend.springai.engine.orchestration.spi;

import ascend.springai.engine.orchestration.spi.RunMode;

import java.util.UUID;

/**
 * Execution context threaded through every node and reasoning step.
 * Pure Java — no Spring imports per architecture §4.7.
 *
 * The single nesting entry-point is suspendForChild: it causes the orchestrator
 * to suspend the current run, start a child run under childMode, and return
 * the child's final result when the child completes. From the caller's view
 * it is a synchronous call; internally it throws SuspendSignal.
 */
public interface RunContext {

    UUID runId();

    String tenantId();

    Checkpointer checkpointer();

    /**
     * W3C-compatible 32-character lowercase hex trace identifier for the in-flight Run.
     * Telemetry Vertical (ADR-0061 / §4 #54). MUST be non-null for a Run carrying a
     * persisted {@code Run.traceId}. SPI purity preserved — returns a plain String,
     * not an OTel type (§4 #7).
     */
    String traceId();

    /**
     * W3C-compatible 16-character lowercase hex span identifier for the current
     * orchestration span. MUST be non-null. New child spans are minted via
     * {@link TraceContext#newChildSpan(String)} through the bound {@link #traceContext()}.
     */
    String spanId();

    /**
     * Optional logical session identifier (ADR-0062 — Trace ↔ Run ↔ Session N:M).
     * MAY be null at L1.x; non-null in posture=research/prod from W2.
     */
    String sessionId();

    /**
     * Trace correlation carrier. L1.x default implementation is
     * {@link ascend.springai.service.runtime.orchestration.NoopTraceContext}; W2 wires an
     * OTel-backed implementation.
     */
    TraceContext traceContext();

    /**
     * Request suspension of the current run and delegation to a child executor.
     *
     * @param parentNodeKey identifies which step in the parent is suspending
     * @param childMode     GRAPH or AGENT_LOOP
     * @param childDef      the definition to hand to the child executor
     * @param resumePayload serialisable data the child should start with
     * @return the child's final result once the child completes
     * @throws SuspendSignal always — caught only by the Orchestrator
     */
    Object suspendForChild(String parentNodeKey, RunMode childMode,
                           ExecutorDefinition childDef, Object resumePayload)
            throws SuspendSignal;
}
