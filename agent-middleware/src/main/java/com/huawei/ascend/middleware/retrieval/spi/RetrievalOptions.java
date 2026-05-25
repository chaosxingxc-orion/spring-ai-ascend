package com.huawei.ascend.middleware.retrieval.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Retrieval tuning knobs.
 *
 * <p>Authority: ADR-0124.
 *
 * @param topK                max hits to return; MUST be &gt; 0.
 * @param scoreThreshold      minimum hybrid-score in [0.0, 1.0].
 * @param filter              optional metadata filter expression;
 *                            null = no filter.
 * @param providerHints       provider-orthogonal hints (e.g.
 *                            {@code rerankerModel}, {@code hybridAlpha}).
 */
public record RetrievalOptions(
        int topK,
        double scoreThreshold,
        String filter,
        Map<String, Object> providerHints) {

    public RetrievalOptions {
        Objects.requireNonNull(providerHints, "providerHints");
        providerHints = Map.copyOf(providerHints);
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be > 0");
        }
    }

    public static RetrievalOptions defaults() {
        return new RetrievalOptions(10, 0.0, null, Map.of());
    }
}
