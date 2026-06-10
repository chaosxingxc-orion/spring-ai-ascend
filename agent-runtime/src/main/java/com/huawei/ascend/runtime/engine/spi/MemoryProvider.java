package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.List;
import java.util.Map;

/**
 * Minimal memory SPI reserved for agent frameworks that need runtime-provided
 * memory initialization and retrieval.
 *
 * <p>This is intentionally smaller than a memory product contract. It gives
 * adapters a place to initialize per-execution memory resources and query
 * relevant memory snippets without coupling the runtime to any one memory
 * backend.
 */
public interface MemoryProvider {

    /** Initialize memory resources for one agent execution. */
    default void init(AgentExecutionContext context) {
    }

    /** Search memory for the current execution. */
    List<MemoryHit> search(AgentExecutionContext context, String query, int limit);

    /** One memory search hit returned by a provider. */
    record MemoryHit(String id, String content, double score, Map<String, Object> metadata) {
        public MemoryHit {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
