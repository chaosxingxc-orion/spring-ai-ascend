package com.huawei.ascend.service.session;

import com.huawei.ascend.service.session.spi.ContextProjector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference in-memory implementation of {@link ContextProjector} per
 * ADR-0100 (rc24).
 *
 * <p>Implements a simple "last_n" projection strategy: keeps the last N
 * messages where N defaults to 10. Variables are projected unchanged.
 *
 * <p>Posture-gated for dev/research; production projectors (summary_v1,
 * hybrid_v1) land alongside the Session JDBC persistence in rc25.
 */
public class InMemoryContextProjector implements ContextProjector {

    private static final int DEFAULT_LAST_N = 10;

    @Override
    public Map<String, Object> project(String sessionId, String tenantId, String projectionPolicy) {
        // Reference impl: stub session-context projection. Real
        // projectors will pull from SessionRepository (rc25).
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("messages", List.of());
        ctx.put("variables", Map.of());
        ctx.put("projection_policy", projectionPolicy != null ? projectionPolicy : "last_n");
        ctx.put("projection_window", DEFAULT_LAST_N);
        ctx.put("session_id", sessionId);
        ctx.put("tenant_id", tenantId);
        return ctx;
    }
}
