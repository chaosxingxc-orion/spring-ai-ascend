package com.huawei.ascend.middleware.vector.spi;

import java.util.Map;
import java.util.Objects;

/**
 * One document stored in a {@link VectorStore}.
 *
 * <p>Authority: ADR-0124.
 *
 * @param documentId        unique within (tenantId, source).
 * @param content           textual content; never null.
 * @param embedding         vector representation; nullable —
 *                          {@code VectorStore.add} embeds when null
 *                          using its configured EmbeddingModel.
 * @param metadata          arbitrary key/value metadata; never null.
 */
public record Document(
        String documentId,
        String content,
        float[] embedding,
        Map<String, Object> metadata) {

    public Document {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(metadata, "metadata");
        embedding = embedding == null ? null : embedding.clone();
        metadata = Map.copyOf(metadata);
    }

    @Override
    public float[] embedding() {
        return embedding == null ? null : embedding.clone();
    }
}
