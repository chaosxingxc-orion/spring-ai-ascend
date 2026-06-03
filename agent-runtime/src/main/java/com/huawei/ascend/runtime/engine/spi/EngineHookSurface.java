package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.middleware.spi.HookPoint;

import java.util.Set;

/**
 * An engine's declaration of which {@link HookPoint} events it fires.
 *
 * <p>The Runtime uses this declaration to determine whether a registered
 * {@code RuntimeMiddleware} will ever observe its subscribed hook on this
 * engine — useful for hot-path optimisation (a middleware that subscribes to
 * {@code BEFORE_LLM_INVOCATION} can be skipped entirely if no registered
 * engine declares that hook).
 *
 * <p>The default implementation declares no hooks. At W2.x Phase 2 the
 * orchestrator fires three mandatory hooks
 * ({@link HookPoint#ON_ERROR}, {@link HookPoint#BEFORE_SUSPENSION},
 * {@link HookPoint#BEFORE_RESUME}) regardless of the engine's surface
 * declaration — those are runtime-level hooks, not engine-level.
 *
 * <p>SPI-pure per CLAUDE.md Rule 32: imports {@code java.*} + agent-middleware
 * SPI only.
 *
 * <p>Authority: ADR-0073; CLAUDE.md Rule 45.
 */
@FunctionalInterface
public interface EngineHookSurface {

    /** The {@link HookPoint} values this engine fires from its own code. */
    Set<HookPoint> supportedHooks();

    /** A surface that declares no hooks — the default for legacy adapters. */
    static EngineHookSurface empty() {
        return Set::of;
    }
}
