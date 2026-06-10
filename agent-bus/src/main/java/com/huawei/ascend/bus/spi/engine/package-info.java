/**
 * agent-bus engine-boundary SPI — the transport-agnostic Service↔Engine
 * contract surface, owned by the Bus & State Hub plane.
 *
 * <p><strong>DESIGN-FROZEN.</strong> This SPI surface is a design artefact
 * only: it MUST NOT be implemented or consumed by production code until a
 * re-opening ADR lifts the freeze. The freeze exists because the contract has
 * never been validated by an implementation and carries known design defects;
 * see {@code docs/logs/plans/2026-06-11-agent-bus-spi-decision.md} for the
 * decision record and {@code docs/contracts/engine-port.v1.yaml} for the
 * contract schema. The boundary of record for engine integration is
 * {@code com.huawei.ascend.runtime.engine.spi}. An architecture test in
 * agent-runtime enforces that no production class depends on this package.
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
 * <p>Symmetry note: the bus plane owns the cross-plane control surfaces — the
 * S2C callback ({@code bus.spi.s2c}) and the engine boundary
 * ({@code bus.spi.engine}). The intended topology is that agent-service drives
 * engines through this contract and agent-runtime implements it, with neither
 * module depending on the other; no such implementation or consumption exists
 * today, per the freeze above.
 *
 * <p>SPI-pure per Rule R-D sub-clause .d: imports restricted to {@code java.*}
 * + same-spi-package siblings.
 */
package com.huawei.ascend.bus.spi.engine;
