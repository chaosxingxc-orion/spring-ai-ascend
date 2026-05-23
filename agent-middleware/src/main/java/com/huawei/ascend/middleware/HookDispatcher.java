package com.huawei.ascend.middleware;

import com.huawei.ascend.middleware.spi.HookContext;
import com.huawei.ascend.middleware.spi.HookOutcome;
import com.huawei.ascend.middleware.spi.HookPoint;
import com.huawei.ascend.middleware.spi.RuntimeMiddleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Dispatches a {@link HookPoint} event across a registered list of
 * {@link RuntimeMiddleware} implementations and aggregates outcomes.
 *
 * <p>Ordering (declared in {@code docs/contracts/engine-hooks.v1.yaml}):
 * before_* and on_* hooks fire in registration order; after_* hooks fire in
 * reverse registration order (LIFO unwind so an outermost middleware can
 * clean up after an innermost one).
 *
 * <p>Failure propagation: default is {@code fail_fast} — the first
 * {@link HookOutcome.Fail} or {@link HookOutcome.ShortCircuit} returned
 * stops further dispatch. {@code on_error} is the exception: it always
 * fires the full chain ({@code best_effort}) so a failing middleware
 * cannot mask the original error. The first non-{@code Proceed} outcome
 * across the chain wins.
 *
 * <p>Authority: ADR-0073; CLAUDE.md Rule 45.
 */
public final class HookDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(HookDispatcher.class);

    private final List<RuntimeMiddleware> middlewares;

    public HookDispatcher(List<RuntimeMiddleware> middlewares) {
        Objects.requireNonNull(middlewares, "middlewares is required");
        this.middlewares = Collections.unmodifiableList(new ArrayList<>(middlewares));
    }

    /** Convenience for the empty case — used by EngineRegistry when no middleware is registered. */
    public static HookDispatcher empty() {
        return new HookDispatcher(List.of());
    }

    /** Visible for tests + introspection. */
    public List<RuntimeMiddleware> middlewares() {
        return middlewares;
    }

    /**
     * Fire a hook event across the registered chain and return the
     * aggregated outcome.
     */
    public HookOutcome fire(HookContext ctx) {
        Objects.requireNonNull(ctx, "ctx is required");
        if (middlewares.isEmpty()) {
            return HookOutcome.proceed();
        }
        if (ctx.point() == HookPoint.ON_ERROR) {
            return fireBestEffort(ctx);
        }
        return fireFailFast(orderedFor(ctx.point()), ctx);
    }

    private HookOutcome fireFailFast(List<RuntimeMiddleware> ordered, HookContext ctx) {
        for (RuntimeMiddleware mw : ordered) {
            HookOutcome outcome = invoke(mw, ctx);
            if (!(outcome instanceof HookOutcome.Proceed)) {
                return outcome;
            }
        }
        return HookOutcome.proceed();
    }

    private HookOutcome fireBestEffort(HookContext ctx) {
        HookOutcome winner = HookOutcome.proceed();
        for (RuntimeMiddleware mw : middlewares) {
            HookOutcome outcome = invoke(mw, ctx);
            if (winner instanceof HookOutcome.Proceed && !(outcome instanceof HookOutcome.Proceed)) {
                winner = outcome;
            }
            // continue firing the rest even if one already produced a non-Proceed outcome
        }
        return winner;
    }

    private HookOutcome invoke(RuntimeMiddleware mw, HookContext ctx) {
        try {
            HookOutcome outcome = mw.onHook(ctx);
            return outcome == null ? HookOutcome.proceed() : outcome;
        } catch (RuntimeException e) {
            LOG.warn("RuntimeMiddleware {} threw on hook {}: {}",
                    mw.getClass().getName(), ctx.point(), e.toString());
            // Middleware exceptions must not tear down the orchestrator.
            return new HookOutcome.Fail("middleware_threw:" + e.getClass().getSimpleName());
        }
    }

    private List<RuntimeMiddleware> orderedFor(HookPoint point) {
        if (isAfterHook(point)) {
            List<RuntimeMiddleware> reversed = new ArrayList<>(middlewares);
            Collections.reverse(reversed);
            return reversed;
        }
        return middlewares;
    }

    private static boolean isAfterHook(HookPoint point) {
        return point == HookPoint.AFTER_LLM_INVOCATION
                || point == HookPoint.AFTER_TOOL_INVOCATION
                || point == HookPoint.AFTER_MEMORY_WRITE;
    }
}
