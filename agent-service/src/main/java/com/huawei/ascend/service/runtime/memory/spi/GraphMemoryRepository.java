package com.huawei.ascend.service.runtime.memory.spi;

import java.util.List;

/**
 * SPI: relationship-aware graph memory store for <em>platform-state or
 * explicitly delegated business state</em> (NOT a default owner of customer
 * business ontology — see ADR-0051 for the memory ownership boundary).
 *
 * <p>No default in-JVM impl (graph structure requires an external store).
 * W1 reference sidecar (): spring-ai-ascend-graphmemory-starter
 * wires a Graphiti REST client at W1; no adapter implementation ships at W0.
 *
 * <p>Ownership semantics: the {@code GraphMetadata} record below is a
 * minimal W0 shape. The full W2 metadata adds a {@code MemoryOwnership}
 * discriminator declaring whether each row is {@code PLATFORM_STATE},
 * {@code DELEGATED_BUSINESS_STATE}, or {@code BOTH}, plus a placeholder-
 * preservation policy. See {@code docs/contracts/plan-projection.v1.yaml}
 * for the design-only counterpart that scopes plan-step memory access.
 *
 * <p>Rule 11: every operation carries tenantId.
 */
public interface GraphMemoryRepository {

    /** Add a fact triple (subject, relation, object) to the tenant-scoped graph (platform or delegated). */
    void addFact(String tenantId, String subject, String relation, String object, GraphMetadata metadata);

    /** Traverse the graph starting from subject, depth-limited. */
    List<GraphEdge> query(String tenantId, String subject, int maxDepth);

    /** Full-text + graph search over the platform or explicitly delegated graph memory (NOT the tenant's business ontology by default). */
    List<GraphEdge> search(String tenantId, String queryText, int topK);

    record GraphEdge(String tenantId, String subject, String relation, String object) {}

    /**
     * Pre-W2 minimal graph-edge metadata subset. Full {@code MemoryMetadata} (including
     * {@code embeddingModelVersion}) lands with the W2 memory implementation.
     */
    record GraphMetadata(String tenantId, String sessionId, String runId, java.time.Instant createdAt) {}
}
