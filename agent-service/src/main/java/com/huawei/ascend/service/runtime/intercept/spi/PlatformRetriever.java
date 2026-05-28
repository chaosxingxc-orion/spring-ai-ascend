package com.huawei.ascend.service.runtime.spi.intercept;

/**
 * RAG retrieval interception entry point.
 *
 * <p>Per ADR-0155 §3, M6 does NOT inject retrieved chunks into prompts.
 * It returns chunk references; the Agent decides whether, where, and
 * how to inject them into its constructed messages.</p>
 *
 * <p>Authority: ADR-0155. Status: design_only.</p>
 */
public interface PlatformRetriever {

    /**
     * Retrieve chunk references from the named index.
     *
     * @param query natural-language query.
     * @param indexRef canonical index identifier.
     * @param topK number of chunks to retrieve.
     * @return retrieval result containing chunk references; the Agent
     *         decides if/how to use them.
     */
    Object retrieve(String query, String indexRef, int topK);
}
