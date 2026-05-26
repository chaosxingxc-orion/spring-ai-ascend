/**
 * Retriever SPI — tenant-scoped composition layer over one or more
 * {@link com.huawei.ascend.middleware.vector.spi.VectorStore}s.
 *
 * <p>Authority: ADR-0124.
 *
 * <p>Reference adapter {@code SpringAiDocumentRetriever} decorates
 * Spring AI's document-retrieval shape defined by ADR-0125.
 *
 * <p>Custom retrievers compose vector search with reranking, hybrid
 * keyword search, or BM25; the SPI surface is intentionally narrow
 * so customers express composition policy in their implementation.
 *
 * <p>SPI purity per Rule R-D.
 */
package com.huawei.ascend.middleware.retrieval.spi;
