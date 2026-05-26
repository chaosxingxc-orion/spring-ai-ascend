package com.huawei.ascend.service.integration.springai;

import com.huawei.ascend.middleware.vector.spi.Document;
import com.huawei.ascend.middleware.vector.spi.VectorMatch;
import com.huawei.ascend.middleware.vector.spi.VectorQuery;
import com.huawei.ascend.middleware.vector.spi.VectorStore;

import java.util.List;
import java.util.Objects;

/**
 * Reference {@link VectorStore} that decorates a Spring AI
 * {@code org.springframework.ai.vectorstore.VectorStore}.
 *
 * <p>Authority: ADR-0124 + ADR-0125. Design-only shell.
 *
 * <p>W3 RAG vertical implementation responsibilities:
 * <ul>
 *   <li>Map platform {@link Document} into Spring AI's
 *       {@code org.springframework.ai.document.Document}.</li>
 *   <li>Filter every read / write to {@code tenantId} by adding
 *       a metadata filter expression Spring AI's vector store
 *       evaluates.</li>
 *   <li>Fire {@code HookPoint.BEFORE_MEMORY} / {@code AFTER_MEMORY}
 *       around add / delete / similaritySearch.</li>
 *   <li>Translate {@link VectorQuery} into Spring AI's
 *       {@code SearchRequest}.</li>
 * </ul>
 *
 * <p>The platform type is held as {@code java.lang.Object} field
 * to defer the FQN to {@code .class} loading time and avoid
 * import collision between the platform {@code VectorStore}
 * interface (this class implements) and Spring AI's
 * {@code VectorStore} interface (this class wraps).
 */
public final class SpringAiVectorStoreAdapter implements VectorStore {

    private final org.springframework.ai.vectorstore.VectorStore springAiVectorStore;

    public SpringAiVectorStoreAdapter(
            org.springframework.ai.vectorstore.VectorStore springAiVectorStore) {
        this.springAiVectorStore = Objects.requireNonNull(
                springAiVectorStore, "springAiVectorStore");
    }

    @Override
    public void add(String tenantId, List<Document> documents) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(documents, "documents");
        throw new UnsupportedOperationException(
                "SpringAiVectorStoreAdapter: design-only shell at L0; "
                        + "W3 RAG vertical wires tenant-scoped add + hook dispatch");
    }

    @Override
    public void delete(String tenantId, List<String> documentIds) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(documentIds, "documentIds");
        throw new UnsupportedOperationException(
                "SpringAiVectorStoreAdapter: design-only shell at L0");
    }

    @Override
    public List<VectorMatch> similaritySearch(String tenantId, VectorQuery query) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(query, "query");
        throw new UnsupportedOperationException(
                "SpringAiVectorStoreAdapter: design-only shell at L0; "
                        + "W3 RAG vertical wires tenant-filtered similarity search");
    }

    /** Exposes the underlying Spring AI bean for diagnostic / wiring assertions. */
    public org.springframework.ai.vectorstore.VectorStore underlyingSpringAiVectorStore() {
        return springAiVectorStore;
    }
}
