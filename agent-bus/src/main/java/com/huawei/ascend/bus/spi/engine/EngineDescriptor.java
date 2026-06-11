package com.huawei.ascend.bus.spi.engine;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * Engine capability descriptor returned by {@link EnginePort#describe}.
 *
 * <p>{@code engineTypes} is required (an engine that advertises nothing is a
 * caller bug, not an empty capability set). {@code health} is required and uses
 * the vocabulary {@code "UP"} / {@code "DOWN"} / {@code "DEGRADED"} so two
 * implementations cannot invent disjoint health dialects.
 */
public record EngineDescriptor(Set<String> engineTypes, String health) implements Serializable {

    public static final String HEALTH_UP = "UP";
    public static final String HEALTH_DOWN = "DOWN";
    public static final String HEALTH_DEGRADED = "DEGRADED";

    public EngineDescriptor {
        Objects.requireNonNull(engineTypes, "engineTypes is required");
        engineTypes = Set.copyOf(engineTypes);
        Objects.requireNonNull(health, "health is required");
        if (!HEALTH_UP.equals(health) && !HEALTH_DOWN.equals(health) && !HEALTH_DEGRADED.equals(health)) {
            throw new IllegalArgumentException("health must be UP, DOWN, or DEGRADED, got: " + health);
        }
    }
}
