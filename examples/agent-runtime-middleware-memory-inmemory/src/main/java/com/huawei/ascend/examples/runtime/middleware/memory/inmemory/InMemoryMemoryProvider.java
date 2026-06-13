package com.huawei.ascend.examples.runtime.middleware.memory.inmemory;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Example-only in-memory {@link MemoryProvider}.
 *
 * <p>Records are scoped by {@link AgentExecutionContext#getAgentStateKey()} so
 * the same provider instance can be reused by multiple sessions in local tests.
 */
public final class InMemoryMemoryProvider implements MemoryProvider {

    private final ConcurrentMap<String, CopyOnWriteArrayList<MemoryRecord>> recordsByStateKey =
            new ConcurrentHashMap<>();

    @Override
    public void init(AgentExecutionContext context) {
        recordsByStateKey.computeIfAbsent(scopeKey(context), ignored -> new CopyOnWriteArrayList<>());
    }

    @Override
    public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
        if (limit <= 0 || !hasText(query)) {
            return List.of();
        }
        String normalizedQuery = normalize(query);
        return recordsByStateKey.getOrDefault(scopeKey(context), new CopyOnWriteArrayList<>()).stream()
                .filter(record -> hasText(record.content()))
                .map(record -> toHit(record, normalizedQuery))
                .filter(hit -> hit.score() != null && hit.score() > 0.0)
                .sorted(Comparator.comparingDouble(InMemoryMemoryProvider::scoreOrLowest).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public void save(AgentExecutionContext context, List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        CopyOnWriteArrayList<MemoryRecord> scopedRecords =
                recordsByStateKey.computeIfAbsent(scopeKey(context), ignored -> new CopyOnWriteArrayList<>());
        for (MemoryRecord record : records) {
            if (record != null && hasText(record.content())) {
                scopedRecords.add(stableRecord(record));
            }
        }
    }

    public List<MemoryRecord> records(AgentExecutionContext context) {
        return List.copyOf(recordsByStateKey.getOrDefault(scopeKey(context), new CopyOnWriteArrayList<>()));
    }

    private static MemoryHit toHit(MemoryRecord record, String normalizedQuery) {
        String normalizedContent = normalize(record.content());
        if (normalizedContent.contains(normalizedQuery)) {
            return new MemoryHit(record.id(), record.content(), 1.0, record.metadata());
        }
        double score = wordOverlapScore(normalizedQuery, normalizedContent);
        return new MemoryHit(record.id(), record.content(), score, record.metadata());
    }

    private static double wordOverlapScore(String normalizedQuery, String normalizedContent) {
        String[] terms = normalizedQuery.split("\\s+");
        int matched = 0;
        for (String term : terms) {
            if (!term.isBlank() && normalizedContent.contains(term)) {
                matched++;
            }
        }
        return terms.length == 0 ? 0.0 : (double) matched / terms.length;
    }

    private static double scoreOrLowest(MemoryHit hit) {
        return hit.score() == null ? Double.NEGATIVE_INFINITY : hit.score();
    }

    private static MemoryRecord stableRecord(MemoryRecord record) {
        String id = hasText(record.id()) ? record.id() : UUID.randomUUID().toString();
        return new MemoryRecord(id, record.role(), record.content(), record.metadata());
    }

    private static String scopeKey(AgentExecutionContext context) {
        return context.getAgentStateKey();
    }

    private static String normalize(String value) {
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
