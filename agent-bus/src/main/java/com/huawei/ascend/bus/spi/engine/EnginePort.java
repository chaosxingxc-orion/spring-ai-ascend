package com.huawei.ascend.bus.spi.engine;

import java.util.concurrent.Flow;

/**
 * The neutral, transport-agnostic Service&#8596;Engine boundary. agent-service drives an engine
 * through this port; agent-runtime implements it; the Service selects the transport by
 * deployment form (in_process / internal_rpc / a2a) so neither module depends on the other.
 *
 * <p>{@link #execute} returns a stream of {@link AgentEvent}; exactly one TERMINAL event
 * (FINISHED / FAILED / INTERRUPT_REQUEST) is emitted, last. Errors and suspension are terminal
 * events, never thrown across the boundary. The in-process realization maps suspension onto the
 * existing {@code SuspendSignal} via an in-JVM outcome channel referenced by the event's handle;
 * a networked realization uses the checkpoint-token protocol (see
 * {@code docs/contracts/engine-port.v1.yaml}). The engine-facing {@link ExecutionContext}
 * carries no tenant/session semantics.
 *
 * <p>Pure Java — no Spring imports.
 */
public interface EnginePort {

    Flow.Publisher<AgentEvent> execute(ExecutionContext ctx, ExecuteRequest request);

    EngineDescriptor describe();
}
