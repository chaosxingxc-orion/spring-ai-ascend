package com.huawei.ascend.middleware.memory.spi;

import java.util.Objects;

/**
 * Opaque reference to a {@link MemoryStore} or specific entry.
 *
 * <p>Authority: ADR-0123. Used by {@code Agent.memoryBindings}
 * and elsewhere to refer to memory targets by id + category.
 *
 * @param memoryId  for binding to a store: store id; for binding
 *                  to a specific entry: entry id.
 * @param category  ADR-0034 / ADR-0123 taxonomy category.
 */
public record MemoryRef(String memoryId, MemoryCategory category) {
    public MemoryRef {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(category, "category");
        if (memoryId.isBlank()) {
            throw new IllegalArgumentException("memoryId must be non-blank");
        }
    }
}
