package com.huawei.ascend.service.session;

import com.huawei.ascend.service.session.spi.ContextProjectionRequest;
import com.huawei.ascend.service.session.spi.ContextProjector;
import com.huawei.ascend.service.session.spi.ProjectedContext;

import java.util.List;
import java.util.Map;

/**
 * Reference in-memory implementation of {@link ContextProjector} per
 * ADR-0100.
 *
 * <p>Implements a simple "last_n" projection strategy: keeps the last N
 * messages where N defaults to 10. Variables are projected unchanged.
 * Stub at L0 — emits an empty {@link ProjectedContext} that reports the
 * resolved policy + projection window; real projectors will pull from
 * {@code SessionRepository} when it lands.
 *
 * <p>Posture-gated for dev/research; production projectors (summary_v1,
 * hybrid_v1) land alongside the Session JDBC persistence.
 */
public class InMemoryContextProjector implements ContextProjector {

    private static final int DEFAULT_LAST_N = 10;
    private static final String DEFAULT_POLICY = "last_n";

    @Override
    public ProjectedContext project(ContextProjectionRequest request) {
        // Reference impl: stub session-context projection. Real projectors will
        // pull from SessionRepository, honour tokenBudget, and resolve memoryRefs.
        String resolvedPolicy = request.projectionPolicy() != null
                ? request.projectionPolicy()
                : DEFAULT_POLICY;
        // last_n always reports the configured window; other policies report 0
        // (they do not use a windowed tail).
        int projectionWindow = DEFAULT_POLICY.equals(resolvedPolicy) ? DEFAULT_LAST_N : 0;
        return new ProjectedContext(
                request.tenantId(),
                request.sessionId(),
                resolvedPolicy,
                projectionWindow,
                List.of(),
                Map.of(),
                /* tokenCount */ 0);
    }
}
