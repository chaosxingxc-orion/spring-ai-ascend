/**
 * Embedding Model SPI — tenant-scoped boundary for text embedding.
 *
 * <p>Authority: ADR-0124.
 *
 * <p>Reference adapter {@code SpringAiEmbeddingModel} decorates Spring AI's
 * {@code org.springframework.ai.embedding.EmbeddingModel} per
 * ADR-0125.
 *
 * <p>{@link EmbeddingModel#modelVersion()} feeds
 * {@code MemoryMetadata.embeddingModelVersion} (ADR-0123) so M5
 * knowledge entries stay replayable after a model upgrade.
 *
 * <p>SPI purity per Rule R-D.
 */
package com.huawei.ascend.middleware.embedding.spi;
