package com.huawei.ascend.middleware.embedding.spi;

import java.util.Objects;

/**
 * One embedding vector.
 *
 * <p>Authority: ADR-0124.
 *
 * @param vector         dense float vector; never null;
 *                       length matches
 *                       {@link EmbeddingModel#dimensionality()}.
 * @param modelVersion   the {@link EmbeddingModel#modelVersion()}
 *                       that produced this embedding.
 */
public record Embedding(float[] vector, String modelVersion) {
    public Embedding {
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(modelVersion, "modelVersion");
        vector = vector.clone();
    }

    @Override
    public float[] vector() {
        return vector.clone();
    }
}
