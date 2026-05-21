package com.huawei.ascend.service.session.spi;

import java.util.Map;

/**
 * Session context projection SPI.
 *
 * <p>Projects a {@code SessionContext} view from full Session history
 * via a configurable truncation / summarization policy. The Engine
 * sees only what the projector exposed; it never reads full Session
 * history directly.
 *
 * <p>Authority: ADR-0100 (Session Manager component). Reference impl
 * ({@code InMemoryContextProjector}) lands
 * implementation timeline.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + own
 * siblings.
 */
public interface ContextProjector {

    /**
     * Project a SessionContext from full Session history.
     *
     * <p>The {@code projectionPolicy} name (e.g., {@code "last_n"},
     * {@code "summary_v1"}, {@code "hybrid_v1"}) is carried in the
     * returned context so downstream observability can trace which
     * strategy was applied.
     *
     * @param sessionId       the Session whose history to project.
     * @param tenantId        mandatory per Rule R-C.c.
     * @param projectionPolicy the named strategy to apply.
     * @return the projected context as a map; carries
     *         {@code messages}, {@code variables}, {@code projection_policy}.
     */
    Map<String, Object> project(String sessionId, String tenantId, String projectionPolicy);
}
