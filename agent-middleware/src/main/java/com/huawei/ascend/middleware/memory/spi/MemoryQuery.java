package com.huawei.ascend.middleware.memory.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Filter / paging envelope for {@link MemoryReader#scan(String, MemoryQuery)}.
 *
 * <p>Authority: ADR-0123.
 *
 * @param category     target category; never null.
 * @param tagFilters   exact-match tag filter; never null, may be empty.
 * @param since        return entries with {@code updatedAt} &gt;= since;
 *                     nullable (no time filter).
 * @param cursor       opaque paging cursor; null = first page.
 * @param pageSize     max entries to return; MUST be &gt; 0.
 */
public record MemoryQuery(
        MemoryCategory category,
        Map<String, String> tagFilters,
        Instant since,
        String cursor,
        int pageSize) {

    public MemoryQuery {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(tagFilters, "tagFilters");
        tagFilters = Map.copyOf(tagFilters);
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
    }
}
