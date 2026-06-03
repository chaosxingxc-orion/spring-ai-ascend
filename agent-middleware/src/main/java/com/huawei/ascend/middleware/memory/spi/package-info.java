/**
 * Memory SPI — tenant-scoped boundary for the M1–M6 memory category
 * taxonomy (ADR-0034) / ADR-0123.
 *
 * <p>Authority: ADR-0123. Lives in {@code agent-middleware} because
 * memory is a cross-cutting middleware concern that sits between
 * the engine and external persistence (vector store, graph store,
 * KV store).
 *
 * <p>The pre-existing {@code GraphMemoryRepository} (M4 reference)
 * in {@code com.huawei.ascend.runtime.memory.spi} remains
 * in place at L0 for backward compatibility; a follow-up wave may
 * migrate it here once the runtime kernel's coupling to it is
 * documented.
 *
 * <p>Unified surface: {@link MemoryStore} = {@link MemoryReader}
 * + {@link MemoryWriter}, parameterized by {@link MemoryCategory}.
 * Marker interfaces {@link SemanticMemoryStore} (M3) and
 * {@link KnowledgeMemoryStore} (M5) extend
 * {@link MemoryStore} with category-specific defaults.
 *
 * <p>Ownership boundary (ADR-0051) lives in
 * {@link MemoryOwnership} ({@code C_SIDE} / {@code S_SIDE} /
 * {@code DELEGATED}); attached to every write through
 * {@link MemoryMetadata}.
 *
 * <p>SPI purity per Rule R-D.
 */
package com.huawei.ascend.middleware.memory.spi;
