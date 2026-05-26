package com.huawei.ascend.middleware.memory.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ordered conversation window stored by {@link ConversationMemory}.
 *
 * <p>Authority: ADR-0133.
 *
 * @param turns    ordered turns; never null.
 * @param metadata implementation-specific window metadata; never null.
 */
public record ConversationWindow(
        List<ConversationTurn> turns,
        Map<String, Object> metadata) {

    public ConversationWindow {
        Objects.requireNonNull(turns, "turns");
        Objects.requireNonNull(metadata, "metadata");
        turns = List.copyOf(turns);
        metadata = Map.copyOf(metadata);
    }
}
