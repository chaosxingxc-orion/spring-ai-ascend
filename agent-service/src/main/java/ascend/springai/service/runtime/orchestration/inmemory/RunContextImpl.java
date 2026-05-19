package ascend.springai.service.runtime.orchestration.inmemory;

import ascend.springai.service.runtime.orchestration.NoopTraceContext;
import ascend.springai.engine.orchestration.spi.Checkpointer;
import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.engine.orchestration.spi.RunContext;
import ascend.springai.engine.orchestration.spi.SuspendSignal;
import ascend.springai.engine.orchestration.spi.TraceContext;
import ascend.springai.engine.orchestration.spi.RunMode;

import java.util.Objects;
import java.util.UUID;

/**
 * RunContext implementation for the in-memory reference executor.
 * suspendForChild always throws SuspendSignal — the Orchestrator catches it.
 *
 * <p>Carries a {@link NoopTraceContext} at L1.x — propagates trace/span/session ids
 * without emitting OTel spans (ADR-0061 §2). W2 swaps this for an OTel-backed
 * implementation via the Orchestrator construction site.
 */
final class RunContextImpl implements RunContext {

    private final String tenantId;
    private final UUID runId;
    private final Checkpointer checkpointer;
    private final TraceContext traceContext;

    RunContextImpl(String tenantId, UUID runId, Checkpointer checkpointer) {
        this(tenantId, runId, checkpointer, NoopTraceContext.newRoot());
    }

    RunContextImpl(String tenantId, UUID runId, Checkpointer checkpointer,
                   TraceContext traceContext) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.runId = Objects.requireNonNull(runId);
        this.checkpointer = Objects.requireNonNull(checkpointer);
        this.traceContext = Objects.requireNonNull(traceContext);
    }

    @Override public UUID runId() { return runId; }
    @Override public String tenantId() { return tenantId; }
    @Override public Checkpointer checkpointer() { return checkpointer; }
    @Override public String traceId() { return traceContext.traceId(); }
    @Override public String spanId() { return traceContext.spanId(); }
    @Override public String sessionId() { return traceContext.sessionId(); }
    @Override public TraceContext traceContext() { return traceContext; }

    @Override
    public Object suspendForChild(String parentNodeKey, RunMode childMode,
                                  ExecutorDefinition childDef, Object resumePayload)
            throws SuspendSignal {
        throw new SuspendSignal(parentNodeKey, resumePayload, childMode, childDef);
    }
}
