/**
 * agent-runtime engine SPI — engine-adapter surface for heterogeneous
 * execution. Holds {@link com.huawei.ascend.runtime.engine.spi.ExecutorAdapter}
 * + its two reference sub-interfaces
 * ({@link com.huawei.ascend.runtime.engine.spi.GraphExecutor},
 * {@link com.huawei.ascend.runtime.engine.spi.AgentLoopExecutor}),
 * the {@link com.huawei.ascend.runtime.engine.spi.EngineHookSurface} declaration,
 * and the {@link com.huawei.ascend.runtime.engine.spi.EngineMatchingException}
 * raised on dispatch mismatch.
 *
 * <p>The payload contract type
 * {@link com.huawei.ascend.bus.spi.engine.ExecutorDefinition}
 * lives in the {@code com.huawei.ascend.bus.spi.engine} package (owned by
 * agent-bus) alongside {@code RunContext} / {@code SuspendSignal}: the neutral
 * orchestration/engine SPI is the transport-agnostic EnginePort boundary owned
 * by the Bus &amp; State Hub plane, which this adapter SPI consumes.
 *
 * <p>SPI-pure per CLAUDE.md Rule 32: imports restricted to {@code java.*} +
 * own spi siblings + cross-module SPI surfaces
 * ({@link com.huawei.ascend.middleware.spi.HookPoint} for hook declarations;
 * {@link com.huawei.ascend.bus.spi.engine.RunContext} /
 * {@code ExecutorDefinition} / {@code SuspendSignal} for adapter signatures).
 * Spring / platform / impl / micrometer imports are forbidden — enforced by
 * {@code SpiPurityGeneralizedArchTest} (E48).
 *
 * <p>Authority: ADR-0072, ADR-0088, Layer-0 principle P-M, CLAUDE.md Rule R-M sub-clauses .a/.b/.c.
 */
package com.huawei.ascend.runtime.engine.spi;
