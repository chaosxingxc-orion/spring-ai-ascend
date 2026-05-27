package com.huawei.ascend.service.session.spi;

import java.util.List;
import java.util.Objects;

/**
 * Typed carrier for {@link ContextProjector#project(ContextProjectionRequest)}.
 *
 * <p>Closes IF-DRIFT-003 from PR #76's interface-drift review: the prior
 * {@code Map<String,Object> project(String sessionId, String tenantId, String projectionPolicy)}
 * signature lost schema and could not carry taskId, token budget, or memory
 * references that L2 projection policies (summary_v1, hybrid_v1) need.
 *
 * <p>Required fields ({@code tenantId}, {@code sessionId}) are non-null-validated
 * in the canonical constructor; optional fields default to neutral values rather
 * than null (taskId may be null when the projection is Session-scoped, not
 * Task-scoped). The record is immutable; {@code memoryRefs} is defensively copied.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + sibling SPI types.
 *
 * @param tenantId         mandatory; the tenant scope. Must match the persisted Session's tenantId.
 * @param sessionId        mandatory; the Session whose history to project.
 * @param taskId           optional; when present, narrows the projection to the Task's slice of the Session.
 * @param projectionPolicy named strategy (e.g. {@code "last_n"}, {@code "summary_v1"}, {@code "hybrid_v1"}).
 *                         When null, the projector chooses a sensible default and reports it back in
 *                         {@link ProjectedContext#projectionPolicy()}.
 * @param tokenBudget      target token budget for the projection (0 = unbounded; the projector
 *                         may exceed this if mandatory context cannot fit).
 * @param memoryRefs       optional memory-reference handles to include in the projection
 *                         (defensively copied; null becomes empty list).
 */
public record ContextProjectionRequest(
        String tenantId,
        String sessionId,
        String taskId,
        String projectionPolicy,
        int tokenBudget,
        List<String> memoryRefs) {

    public ContextProjectionRequest {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        memoryRefs = memoryRefs == null ? List.of() : List.copyOf(memoryRefs);
        if (tokenBudget < 0) {
            throw new IllegalArgumentException("tokenBudget must be >= 0 (0 means unbounded), got " + tokenBudget);
        }
    }

    /**
     * Convenience constructor for the Session-scoped, unbounded, default-policy case
     * — the minimum-grounding form for L1 callers that haven't promoted to L2 policy yet.
     */
    public static ContextProjectionRequest sessionScoped(String tenantId, String sessionId) {
        return new ContextProjectionRequest(tenantId, sessionId, null, null, 0, List.of());
    }
}
