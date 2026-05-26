package com.huawei.ascend.middleware.memory.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One ordered conversation turn owned by the memory SPI package.
 *
 * <p>Authority: ADR-0133.
 *
 * @param role       role of the observed turn; never null.
 * @param content    textual turn content; never null, may be empty.
 * @param observedAt server-side observation timestamp; never null.
 * @param tokenCount implementation-tokenizer count for this turn's
 *                   content; non-negative.
 * @param metadata   implementation-specific turn metadata; never null.
 */
public record ConversationTurn(
        ConversationRole role,
        String content,
        Instant observedAt,
        int tokenCount,
        Map<String, Object> metadata) {

    public ConversationTurn {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(metadata, "metadata");
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must be non-negative");
        }
        metadata = Map.copyOf(metadata);
    }
}
