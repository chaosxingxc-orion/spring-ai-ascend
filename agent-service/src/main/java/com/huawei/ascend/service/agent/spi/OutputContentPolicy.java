package com.huawei.ascend.service.agent.spi;

import java.util.Objects;
import java.util.Set;

/**
 * Content policy for outputs.
 *
 * <p>Authority: ADR-0128 + ADR-0051.
 *
 * @param redactPiiCategories PII categories to redact
 *                            ({@code EMAIL} / {@code PHONE} /
 *                            {@code SSN} / {@code CREDIT_CARD} /
 *                            ...). Empty = no redaction.
 * @param maxOutputChars      hard cap on output length; -1 = unbounded.
 */
public record OutputContentPolicy(Set<String> redactPiiCategories, int maxOutputChars) {

    public OutputContentPolicy {
        Objects.requireNonNull(redactPiiCategories, "redactPiiCategories");
        redactPiiCategories = Set.copyOf(redactPiiCategories);
    }

    public static OutputContentPolicy defaults() {
        return new OutputContentPolicy(Set.of(), -1);
    }
}
