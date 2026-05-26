package com.huawei.ascend.middleware.retrieval.spi;

import java.util.List;

/**
 * Tenant-scoped retrieval primitive.
 *
 * <p>Authority: ADR-0124. Composes one or more vector stores
 * (and optionally keyword indices) into a single
 * {@code retrieve(...)} call.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package siblings.
 */
public interface Retriever {

    /**
     * Retrieve top documents for a free-text query.
     *
     * @return ordered hits (most relevant first); never null,
     *         possibly empty.
     */
    List<RetrievedDocument> retrieve(String tenantId, String query, RetrievalOptions options);
}
