package com.huawei.ascend.engine.planner.spi;

import java.util.Objects;

/**
 * Opaque reference to a {@link Planner} by id. Used by
 * {@code Agent.plannerBinding} (Wave B5).
 *
 * <p>Authority: ADR-0126.
 */
public record PlannerRef(String plannerId) {
    public PlannerRef {
        Objects.requireNonNull(plannerId, "plannerId");
        if (plannerId.isBlank()) {
            throw new IllegalArgumentException("plannerId must be non-blank");
        }
    }
}
