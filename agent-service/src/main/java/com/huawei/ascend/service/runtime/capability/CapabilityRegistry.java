package com.huawei.ascend.service.runtime.capability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves a {@code capabilityName} to its {@link Capability}.
 *
 * <p>Built from every {@link Capability} bean in the application context. A
 * duplicate {@code capabilityName} is a wiring error and fails fast at
 * construction rather than silently shadowing one bean with another.
 */
public final class CapabilityRegistry {

    private final Map<String, Capability> byName;

    public CapabilityRegistry(List<Capability> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        Map<String, Capability> map = new HashMap<>();
        for (Capability capability : capabilities) {
            String name = capability.capabilityName();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException(
                        "Capability " + capability.getClass().getName() + " declares a blank capabilityName");
            }
            Capability previous = map.putIfAbsent(name, capability);
            if (previous != null) {
                throw new IllegalStateException("Duplicate capabilityName '" + name + "' declared by "
                        + previous.getClass().getName() + " and " + capability.getClass().getName());
            }
        }
        this.byName = Map.copyOf(map);
    }

    public Optional<Capability> resolve(String capabilityName) {
        return Optional.ofNullable(byName.get(capabilityName));
    }
}
