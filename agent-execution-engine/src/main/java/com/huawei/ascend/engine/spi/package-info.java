/**
 * agent-execution-engine SPI — engine-adapter surface for heterogeneous
 * execution. Holds {@link com.huawei.ascend.engine.spi.ExecutorAdapter}
 * + its two reference sub-interfaces
 * ({@link com.huawei.ascend.engine.spi.GraphExecutor},
 * {@link com.huawei.ascend.engine.spi.AgentLoopExecutor}),
 * the {@link com.huawei.ascend.engine.spi.EngineHookSurface} declaration,
 * and the {@link com.huawei.ascend.engine.spi.EngineMatchingException}
 * raised on dispatch mismatch.
 *
 * <p>The payload contract type
 * {@link com.huawei.ascend.engine.orchestration.spi.ExecutorDefinition}
 * lives in the sibling {@code engine.orchestration.spi} package alongside
 * {@code RunContext} / {@code SuspendSignal} (relocated here from the
 * dissolved {@code agent-runtime-core} module / rc13) so the
 * orchestration vocabulary stays co-located with the engine that
 * discriminates over it.
 *
 * <p>SPI-pure per CLAUDE.md Rule 32: imports restricted to {@code java.*} +
 * own spi siblings + cross-module SPI surfaces
 * ({@link com.huawei.ascend.middleware.spi.HookPoint} for hook declarations;
 * {@link com.huawei.ascend.engine.orchestration.spi.RunContext} /
 * {@code ExecutorDefinition} / {@code SuspendSignal} for adapter signatures).
 * Spring / platform / impl / micrometer imports are forbidden — enforced by
 * {@code SpiPurityGeneralizedArchTest} (E48).
 *
 * <p>Authority: ADR-0072, ADR-0088, Layer-0 principle P-M, CLAUDE.md Rule R-M sub-clauses .a/.b/.c.
 */
package com.huawei.ascend.engine.spi;
