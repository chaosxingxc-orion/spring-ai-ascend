package ascend.springai.engine.spi;

import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.engine.orchestration.spi.RunContext;
import ascend.springai.engine.orchestration.spi.SuspendSignal;

/**
 * Uniform dispatch surface for every execution engine known to the runtime.
 *
 * <p>The runtime never pattern-matches on {@link ExecutorDefinition} subtypes
 * directly — that is forbidden outside of {@code engine.EngineRegistry} per
 * Rule 43. All dispatch goes through {@code EngineRegistry.resolve(envelope)}
 * which returns one of these adapters; the adapter then runs the payload.
 *
 * <p>An adapter declares its {@link #engineType()} (e.g. {@code "graph"},
 * {@code "agent-loop"}). The {@link #execute(RunContext, ExecutorDefinition, Object)}
 * default contract is: cast the {@link ExecutorDefinition} to the concrete
 * subtype the adapter understands, or raise {@link EngineMatchingException}.
 *
 * <p>The two reference SPIs in this package — {@link GraphExecutor} and
 * {@link AgentLoopExecutor} — extend this interface so that every existing
 * implementation is automatically an {@code ExecutorAdapter}.
 *
 * <p>SPI-pure per CLAUDE.md Rule 32: imports restricted to {@code java.*}
 * + same-package siblings + cross-module SPI surfaces
 * ({@code service.runtime.orchestration.spi}, {@code middleware.spi}).
 * Spring / platform / impl / metrics imports are forbidden (E48).
 *
 * <p>Authority: ADR-0072; CLAUDE.md Rules 43, 44.
 */
public interface ExecutorAdapter {

    /**
     * Stable identifier for this engine. MUST match a {@code known_engines[].id}
     * entry in {@code docs/contracts/engine-envelope.v1.yaml} once the EngineRegistry
     * boot-time check (Phase 5 R2 pilot) is wired.
     */
    String engineType();

    /**
     * The {@link ExecutorDefinition} subtype this adapter understands.
     * Used by {@code EngineRegistry.resolveByPayload(def)} so dispatch never
     * pattern-matches on {@code ExecutorDefinition} subtypes outside the
     * registry (Rule 43).
     */
    Class<? extends ExecutorDefinition> payloadType();

    /**
     * Execute the payload. Implementations should validate the payload type and
     * raise {@link EngineMatchingException} on mismatch — never silently
     * reinterpret a payload as a different engine's configuration.
     */
    Object execute(RunContext ctx, ExecutorDefinition def, Object payload) throws SuspendSignal;

    /**
     * Declaration of which {@link ascend.springai.middleware.spi.HookPoint}
     * events this engine fires from its own code. Default is
     * {@link EngineHookSurface#empty()} — appropriate for adapters whose hook
     * firing happens at the orchestrator layer ({@code ON_ERROR},
     * {@code BEFORE_SUSPENSION}, {@code BEFORE_RESUME}) and who do not yet
     * fire LLM/tool/memory hooks themselves. W2 engines that fire engine-side
     * hooks override this to advertise their hook surface so the runtime can
     * hot-path-skip middlewares whose subscribed hook is unsupported.
     * ADR-0073 §Decision Phase 2.
     */
    default EngineHookSurface hookSurface() {
        return EngineHookSurface.empty();
    }
}
