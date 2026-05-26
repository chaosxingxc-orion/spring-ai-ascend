package com.huawei.ascend.service.integration.springai;

import com.huawei.ascend.middleware.retrieval.spi.RetrievalOptions;
import com.huawei.ascend.middleware.retrieval.spi.RetrievedDocument;
import com.huawei.ascend.middleware.retrieval.spi.Retriever;
import com.huawei.ascend.middleware.vector.spi.VectorStore;

import java.util.List;
import java.util.Objects;

/**
 * Reference {@link Retriever} that composes a platform
 * {@link VectorStore} (typically a
 * {@link SpringAiVectorStoreAdapter}) with an optional reranker
 * policy.
 *
 * <p>Authority: ADR-0124 + ADR-0125. Design-only shell.
 *
 * <p>The constructor takes the platform's {@link VectorStore}
 * (not Spring AI's) so the adapter is portable across non-Spring-AI
 * vector backends (e.g., a future Ascend-NPU vector engine).
 */
public final class SpringAiDocumentRetrieverAdapter implements Retriever {

    private final VectorStore platformVectorStore;

    public SpringAiDocumentRetrieverAdapter(VectorStore platformVectorStore) {
        this.platformVectorStore = Objects.requireNonNull(
                platformVectorStore, "platformVectorStore");
    }

    @Override
    public List<RetrievedDocument> retrieve(String tenantId, String query, RetrievalOptions options) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(options, "options");
        throw new UnsupportedOperationException(
                "SpringAiDocumentRetrieverAdapter: design-only shell at L0; "
                        + "W3 RAG vertical composes similaritySearch + optional rerank");
    }
}
