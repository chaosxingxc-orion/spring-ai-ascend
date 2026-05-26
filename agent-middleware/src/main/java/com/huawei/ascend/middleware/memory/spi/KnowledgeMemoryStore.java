package com.huawei.ascend.middleware.memory.spi;

/**
 * M5 knowledge index marker interface (ADR-0123).
 *
 * <p>Impls typically compose a {@code VectorStore} + {@code Retriever}
 * + {@code EmbeddingModel} and implement
 * {@link MemoryStore} on top.
 *
 * <p>{@link MemoryStore#category()} returns
 * {@link MemoryCategory#M5_KNOWLEDGE} by default.
 */
public interface KnowledgeMemoryStore extends MemoryStore<String, KnowledgeDocument> {

    @Override
    default MemoryCategory category() {
        return MemoryCategory.M5_KNOWLEDGE;
    }
}
