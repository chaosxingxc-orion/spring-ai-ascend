package com.huawei.ascend.middleware.vector.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Vector similarity query.
 *
 * <p>Authority: ADR-0124. Exactly one of {@code queryText} or
 * {@code queryEmbedding} MUST be set.
 *
 * @param queryText               free-text query; null when caller
 *                                supplies a pre-computed embedding.
 * @param queryEmbedding          pre-computed embedding; null when
 *                                caller supplies free text (the store
 *                                embeds via its configured
 *                                EmbeddingModel).
 * @param topK                    max hits to return; MUST be &gt; 0.
 * @param similarityThreshold     minimum similarity in [0.0, 1.0];
 *                                hits below the threshold are
 *                                excluded.
 * @param filter                  optional metadata filter (provider-
 *                                interpreted expression string;
 *                                e.g. {@code "category == 'faq'"}).
 *                                Null = no filter.
 * @param embeddingModelVersion   if {@code queryText} is set and the
 *                                store re-embeds, the model version
 *                                used MUST match this hint when
 *                                non-null; mismatch returns empty.
 */
public record VectorQuery(
        String queryText,
        float[] queryEmbedding,
        int topK,
        double similarityThreshold,
        String filter,
        String embeddingModelVersion,
        Map<String, Object> providerHints) {

    public VectorQuery {
        Objects.requireNonNull(providerHints, "providerHints");
        queryEmbedding = queryEmbedding == null ? null : queryEmbedding.clone();
        providerHints = Map.copyOf(providerHints);
        if (queryText == null && queryEmbedding == null) {
            throw new IllegalArgumentException(
                    "exactly one of queryText or queryEmbedding must be set");
        }
        if (queryText != null && queryEmbedding != null) {
            throw new IllegalArgumentException(
                    "exactly one of queryText or queryEmbedding must be set");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be > 0");
        }
    }

    @Override
    public float[] queryEmbedding() {
        return queryEmbedding == null ? null : queryEmbedding.clone();
    }
}
