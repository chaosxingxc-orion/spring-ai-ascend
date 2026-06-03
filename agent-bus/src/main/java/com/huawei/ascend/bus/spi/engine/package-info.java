/**
 * agent-bus engine-boundary SPI — the transport-agnostic Service↔Engine
 * contract surface, owned by the Bus & State Hub plane.
 *
 * <p>Authority: ADR-0158 (Engine Boundary / EnginePort). The neutral execution
 * model (ExecutorDefinition, RunContext, SuspendSignal, Checkpointer,
 * Orchestrator, RunMode, TraceContext) was relocated here from
 * {@code com.huawei.ascend.engine.orchestration.spi} so the contract is owned
 * by no single engine and survives extraction of agent-runtime into
 * its own repository.
 *
 * <p>The transport-agnostic boundary additionally comprises the neutral,
 * engine-facing {@link com.huawei.ascend.bus.spi.engine.ExecutionContext} (the
 * tenant/session-free supertype of {@code RunContext}), the wire request
 * {@link com.huawei.ascend.bus.spi.engine.ExecuteRequest} (carrying a
 * {@link com.huawei.ascend.bus.spi.engine.DefinitionRef}, never an inline
 * definition), the streamed {@link com.huawei.ascend.bus.spi.engine.AgentEvent}
 * (exactly one TERMINAL event per execution leg), the
 * {@link com.huawei.ascend.bus.spi.engine.EngineDescriptor} returned by
 * {@code describe()}, and the bidirectional
 * {@link com.huawei.ascend.bus.spi.engine.DefinitionResolver} that bridges a
 * {@code DefinitionRef} to a runnable {@code ExecutorDefinition}.
 * {@link com.huawei.ascend.bus.spi.engine.EnginePort#execute} returns a
 * {@link java.util.concurrent.Flow.Publisher} of events rather than throwing
 * across the boundary; {@code java.util.concurrent.Flow} is part of {@code java.*}
 * and is allowed under the SPI-purity rule below.
 *
 * <p>Symmetry note: the bus plane owns the cross-plane control surfaces — C2S
 * ingress ({@code bus.spi.ingress}), S2C callback ({@code bus.spi.s2c}), A2A
 * federation ({@code bus.spi.federation}), and the engine boundary
 * ({@code bus.spi.engine}). agent-service drives engines through this contract;
 * agent-runtime implements it. Neither module depends on the other.
 *
 * <p>SPI-pure per Rule R-D sub-clause .d: imports restricted to {@code java.*}
 * + same-spi-package siblings.
 */
package com.huawei.ascend.bus.spi.engine;
