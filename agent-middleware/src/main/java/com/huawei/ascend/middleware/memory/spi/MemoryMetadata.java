package com.huawei.ascend.middleware.memory.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Common metadata envelope across every {@link MemoryCategory}.
 *
 * <p>Authority: ADR-0123 (formalises ADR-0034 prose into Java).
 *
 * @param memoryId             stable id within (tenantId, category).
 * @param createdAt            initial write timestamp.
 * @param updatedAt            most-recent write timestamp.
 * @param createdBy            actor id (Agent id or user id).
 * @param sourceTraceId        correlation trace id of the write.
 * @param embeddingModelVersion required for M3 / M5; null for
 *                              non-vector categories.
 * @param ownership            C-side vs S-side ownership (ADR-0051).
 * @param tags                 free-form tags.
 */
public record MemoryMetadata(
        String memoryId,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String sourceTraceId,
        String embeddingModelVersion,
        MemoryOwnership ownership,
        Map<String, String> tags) {

    public MemoryMetadata {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(ownership, "ownership");
        Objects.requireNonNull(tags, "tags");
        tags = Map.copyOf(tags);
    }
}
