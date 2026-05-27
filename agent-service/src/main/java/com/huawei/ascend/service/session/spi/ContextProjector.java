package com.huawei.ascend.service.session.spi;

/**
 * Session context projection SPI.
 *
 * <p>Projects a {@link ProjectedContext} view from full Session history
 * via a configurable truncation / summarization policy. The Engine
 * sees only what the projector exposed; it never reads full Session
 * history directly.
 *
 * <p>Authority: ADR-0100 (Session Manager component). Reference impl
 * ({@code InMemoryContextProjector}) lands as the dev-posture stub.
 *
 * <p>SPI shape revised in Wave 5 of the post-merge audit plan
 * (IF-DRIFT-003 closure): the prior {@code Map<String,Object>} carrier
 * was too narrow to express taskId scope, token budget, or memory references
 * that L2 projection policies need. Typed records
 * {@link ContextProjectionRequest} (input) + {@link ProjectedContext} (output)
 * make the SPI schema-bearing.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + own
 * siblings.
 */
public interface ContextProjector {

    /**
     * Project a {@link ProjectedContext} from full Session history per the
     * request's policy + budget + memory references.
     *
     * <p>Implementations MUST:
     * <ul>
     *   <li>Honour {@code request.tenantId()} as the tenant scope — cross-tenant
     *       reads MUST return a result whose {@code messages} + {@code variables}
     *       are empty (or throw an {@link IllegalArgumentException} with a clear
     *       tenant-scope diagnostic, depending on impl posture). Never leak
     *       foreign tenant context.</li>
     *   <li>Set {@code result.projectionPolicy()} to the strategy actually
     *       applied (when the request's policy was null, the impl reports its
     *       default).</li>
     *   <li>Bound the result by {@code request.tokenBudget()} when non-zero;
     *       the impl MAY exceed the budget for mandatory context but MUST report
     *       the actual {@code tokenCount} in the result.</li>
     * </ul>
     *
     * @param request typed projection input; non-null.
     * @return projected context for the request's tenant + session scope; never null.
     */
    ProjectedContext project(ContextProjectionRequest request);
}
