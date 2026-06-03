/**
 * Planner SPI — tenant-scoped boundary for goal → plan generation.
 *
 * <p>Authority: ADR-0126 (extends ADR-0032). Lives in
 * {@code agent-runtime} because the planner produces
 * {@link Plan} DAGs that the engine consumes — Planner output is
 * directly executable by the engine's registry pattern, distinct
 * from the cross-cutting model/skill/memory primitives that live
 * in {@code agent-middleware}.
 *
 * <p>Distinguishes planner OUTPUT ({@link Plan} DAG) from
 * {@code docs/contracts/plan-projection.v1.yaml} which describes
 * per-step scheduler INPUT derived from {@link PlanStep}.
 *
 * <p>Threading model: blocking signatures backed by virtual
 * threads.
 *
 * <p>SPI purity per Rule R-D: imports {@code java.*} +
 * same-module siblings only (Planner is self-contained — no
 * cross-module references in the L0 design).
 */
package com.huawei.ascend.runtime.engine.planner.spi;
