package com.huawei.ascend.bus.spi.engine;

import java.io.Serializable;
import java.util.Set;

/** Engine capability descriptor returned by {@link EnginePort#describe}. */
public record EngineDescriptor(Set<String> engineTypes, String health) implements Serializable {
    public EngineDescriptor {
        engineTypes = Set.copyOf(engineTypes == null ? Set.of() : engineTypes);
    }
}
