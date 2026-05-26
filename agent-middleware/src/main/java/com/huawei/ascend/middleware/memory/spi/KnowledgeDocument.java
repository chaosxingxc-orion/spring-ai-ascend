package com.huawei.ascend.middleware.memory.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Value type for M5 ({@link MemoryCategory#M5_KNOWLEDGE}) entries.
 *
 * <p>Authority: ADR-0123. Typically composes with a
 * {@link com.huawei.ascend.middleware.vector.spi.VectorStore} for
 * the embedding storage layer and an
 * {@link com.huawei.ascend.middleware.embedding.spi.EmbeddingModel}
 * for chunk embedding.
 *
 * @param documentId      stable id within (tenantId, knowledgeBase).
 * @param knowledgeBaseId logical corpus this document belongs to.
 * @param title           free-form title.
 * @param content         full text content.
 * @param chunkingPolicy  declarative chunking strategy
 *                        (e.g. {@code "sentence:512"},
 *                        {@code "paragraph"}, {@code "fixed:1024"}).
 * @param attributes      arbitrary attributes (source URL, language,
 *                        publication date, ...).
 */
public record KnowledgeDocument(
        String documentId,
        String knowledgeBaseId,
        String title,
        String content,
        String chunkingPolicy,
        Map<String, Object> attributes) {

    public KnowledgeDocument {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(chunkingPolicy, "chunkingPolicy");
        Objects.requireNonNull(attributes, "attributes");
        attributes = Map.copyOf(attributes);
    }
}
