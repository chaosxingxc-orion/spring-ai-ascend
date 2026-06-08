/**
 * The sole owner of task lifecycle: creation, the state machine, interrupt /
 * resume / cancel, parent-child orchestration, and idempotency.
 *
 * <p>Control is the single authority. It exposes a strongly-typed API
 * ({@link com.huawei.ascend.runtime.control.api.TaskControlApi}: submit / resume
 * / cancel) rather than a generic {@code publish(event)} surface, and it is the
 * single writer of authority: every engine outcome arriving through
 * {@link com.huawei.ascend.runtime.control.EngineTaskControlAdapter} is applied
 * to the authoritative task record FIRST, and caller-facing egress is fanned out
 * only when control ACCEPTED the transition. This is why the engine never writes
 * authority and output independently (no double-write).
 *
 * <p>What it does not do: parse A2A, execute agents, or manage an agent session.
 * Dependency rule: may depend on {@code common}, {@code session.api},
 * {@code queue} and {@code engine.api}; must not depend on Spring Boot, an agent
 * framework, or {@code access.a2a}. The only boundary sub-package is {@code api}.
 */
package com.huawei.ascend.runtime.control;
