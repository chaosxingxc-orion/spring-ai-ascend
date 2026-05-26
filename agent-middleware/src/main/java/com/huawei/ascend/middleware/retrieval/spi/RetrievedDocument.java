package com.huawei.ascend.middleware.retrieval.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Document returned by {@link Retriever}.
 *
 * <p>Authority: ADR-0124.
 *
 * @param documentId unique within tenant/source; never null.
 * @param content    textual content; never null.
 * @param embedding  optional vector representation.
 * @param metadata   arbitrary key/value metadata; never null.
 */
public record RetrievedDocument(
        String documentId,
        String content,
        float[] embedding,
        Map<String, Object> metadata) {

    public RetrievedDocument {
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
