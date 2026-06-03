package com.huawei.ascend.runtime.engine.planner.spi;

/**
 * Tenant-scoped planner boundary.
 *
 * <p>Authority: ADR-0126.
 *
 * <p>Implementations:
 * <ul>
 *   <li>MUST validate {@link PlanningRequest#tenantId()}
 *       (Rule R-C.c).</li>
 *   <li>MUST honor {@link PlanningRequest#budget()} — exceeding
 *       budget returns
 *       {@link PlanningResult.PlanningInfeasible}.</li>
 *   <li>MAY call LLMs / consult skills / read memory; do so
 *       through the platform's gateways so hooks fire.</li>
 *   <li>MUST be thread-safe; the runtime invokes from virtual
 *       threads.</li>
 * </ul>
 *
 * <p>SPI purity per Rule R-D.
 */
public interface Planner {

    /**
     * Produce a plan for the given request, or report infeasibility.
     */
    PlanningResult plan(PlanningRequest request);

    /** Stable id for registry lookup. Default = simple class name. */
    default String plannerId() {
        return getClass().getSimpleName();
    }
}
