/**
 * agent-execution-engine SPI — engine-adapter surface for heterogeneous
 * execution. Holds {@link ascend.springai.engine.spi.ExecutorAdapter}
 * + its two reference sub-interfaces
 * ({@link ascend.springai.engine.spi.GraphExecutor},
 * {@link ascend.springai.engine.spi.AgentLoopExecutor}),
 * the {@link ascend.springai.engine.spi.EngineHookSurface} declaration,
 * and the {@link ascend.springai.engine.spi.EngineMatchingException}
 * raised on dispatch mismatch.
 *
 * <p>The payload contract type
 * {@link ascend.springai.service.runtime.orchestration.spi.ExecutorDefinition}
 * lives in {@code agent-runtime-core} alongside {@code RunContext} /
 * {@code SuspendSignal} so the runtime kernel stays free of engine-specific
 * adapter classes.
 *
 * <p>SPI-pure per CLAUDE.md Rule 32: imports restricted to {@code java.*} +
 * own spi siblings + cross-module SPI surfaces
 * ({@link ascend.springai.middleware.spi.HookPoint} for hook declarations;
 * {@link ascend.springai.service.runtime.orchestration.spi.RunContext} /
 * {@code ExecutorDefinition} / {@code SuspendSignal} for adapter signatures).
 * Spring / platform / impl / micrometer imports are forbidden — enforced by
 * {@code SpiPurityGeneralizedArchTest} (E48).
 *
 * <p>Authority: ADR-0072, ADR-0079, Layer-0 principle P-M, Rule 43, Rule 44, Rule 45.
 */
package ascend.springai.engine.spi;
