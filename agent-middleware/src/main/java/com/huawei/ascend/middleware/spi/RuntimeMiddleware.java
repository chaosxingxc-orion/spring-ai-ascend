package com.huawei.ascend.middleware.spi;

/**
 * SPI for cross-cutting Runtime-owned policies (proposal §5 of 2026-05-15).
 *
 * <p>Implementations listen on one or more {@link HookPoint} events and
 * return a {@link HookOutcome} that the dispatcher aggregates. Examples
 * (W2 scope, NOT shipped at W2.x):
 *
 * <ul>
 *   <li>{@code TokenCounterHook} — listens on {@link HookPoint#AFTER_LLM_INVOCATION}, counts tokens.</li>
 *   <li>{@code PiiRedactionHook} — listens on {@link HookPoint#BEFORE_LLM_INVOCATION}, redacts payload.</li>
 *   <li>{@code CostAttributionHook} — listens on {@link HookPoint#AFTER_LLM_INVOCATION}, emits cost metric.</li>
 *   <li>{@code LlmSpanEmitterHook} — listens on LLM hooks, emits OTLP spans.</li>
 *   <li>{@code TenantPolicyHook} — listens on every hook, asserts tenant scope.</li>
 * </ul>
 *
 * <p>Engines MUST NOT depend on concrete middleware implementations — the
 * dependency direction is engine → {@link HookPoint} + {@code HookDispatcher.fire(...)}
 * only (proposal §5.4 boundary).
 *
 * <p>Pure Java — no Spring imports per architecture §4.7
 * (orchestration.spi imports only java.*).
 *
 * <p>Authority: ADR-0073; CLAUDE.md Rule 45.
 */
@FunctionalInterface
public interface RuntimeMiddleware {

    /**
     * React to a hook event. Default for an unrelated hook point is
     * {@link HookOutcome#proceed()} — middlewares only override for their
     * subscribed hook points.
     */
    HookOutcome onHook(HookContext ctx);
}
