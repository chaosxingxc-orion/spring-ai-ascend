package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.List;
import java.util.Map;

/**
 * Minimal memory SPI reserved for agent frameworks that need runtime-provided
 * memory initialization, retrieval, and write-back.
 *
 * <p>This is intentionally smaller than a memory product contract. It gives
 * adapters a place to initialize per-execution memory resources, query
 * relevant memory snippets, and save normalized message records without
 * coupling the runtime to any one memory backend.
 */
public interface MemoryProvider {

    /** Initialize memory resources for one agent execution. */
    default void init(AgentExecutionContext context) {
    }

    /** Search memory for the current execution. */
    List<MemoryHit> search(AgentExecutionContext context, String query, int limit);

    /** Save normalized memory records for the current execution. */
    default void save(AgentExecutionContext context, List<MemoryRecord> records) {
    }

    /** One memory search hit returned by a provider. */
    record MemoryHit(String id, String content, double score, Map<String, Object> metadata) {
        public MemoryHit {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    /** One normalized message-like memory record accepted by a provider. */
    record MemoryRecord(String id, String role, String content, Map<String, Object> metadata) {
        public MemoryRecord {
            role = role == null || role.isBlank() ? "unknown" : role;
            content = content == null ? "" : content;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
