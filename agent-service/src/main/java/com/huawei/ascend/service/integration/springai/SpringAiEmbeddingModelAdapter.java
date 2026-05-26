package com.huawei.ascend.service.integration.springai;

import com.huawei.ascend.middleware.embedding.spi.Embedding;
import com.huawei.ascend.middleware.embedding.spi.EmbeddingModel;

import java.util.List;
import java.util.Objects;

/**
 * Reference {@link EmbeddingModel} that decorates a Spring AI
 * {@code org.springframework.ai.embedding.EmbeddingModel}.
 *
 * <p>Authority: ADR-0124 + ADR-0125. Design-only shell.
 *
 * <p>The Spring AI type is held by FQN to avoid name collision
 * with this class's implemented platform interface
 * ({@code com.huawei.ascend.middleware.embedding.spi.EmbeddingModel}).
 */
public final class SpringAiEmbeddingModelAdapter implements EmbeddingModel {

    private final org.springframework.ai.embedding.EmbeddingModel springAiEmbeddingModel;
    private final String modelVersion;
    private final int dimensionality;

    public SpringAiEmbeddingModelAdapter(
            org.springframework.ai.embedding.EmbeddingModel springAiEmbeddingModel,
            String modelVersion,
            int dimensionality) {
        this.springAiEmbeddingModel = Objects.requireNonNull(
                springAiEmbeddingModel, "springAiEmbeddingModel");
        this.modelVersion = Objects.requireNonNull(modelVersion, "modelVersion");
        if (dimensionality <= 0) {
            throw new IllegalArgumentException("dimensionality must be > 0");
        }
        this.dimensionality = dimensionality;
    }

    @Override
    public Embedding embed(String tenantId, String text) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(text, "text");
        throw new UnsupportedOperationException(
                "SpringAiEmbeddingModelAdapter: design-only shell at L0; "
                        + "W3 RAG vertical wires tenant-scoped embed + hook dispatch");
    }

    @Override
    public List<Embedding> embedAll(String tenantId, List<String> texts) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(texts, "texts");
        throw new UnsupportedOperationException(
                "SpringAiEmbeddingModelAdapter: design-only shell at L0");
    }

    @Override
    public String modelVersion() {
        return modelVersion;
    }

    @Override
    public int dimensionality() {
        return dimensionality;
    }

    /** Exposes the underlying Spring AI bean. */
    public org.springframework.ai.embedding.EmbeddingModel underlyingSpringAiEmbeddingModel() {
        return springAiEmbeddingModel;
    }
}
