/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process-local {@link MemoryProvider} for deep-research tests and local demos.
 *
 * <p>Records are scoped by {@code tenantId:userId} so recall works across A2A
 * sessions with the same user identity.
 */
public final class DeepResearchInMemoryMemoryProvider implements MemoryProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DeepResearchInMemoryMemoryProvider.class);

    private final ConcurrentMap<String, CopyOnWriteArrayList<MemoryRecord>> recordsByUser =
            new ConcurrentHashMap<>();

    @Override
    public void init(AgentExecutionContext context) {
        String key = scopeKey(context);
        LOG.info("[DEEP-RESEARCH-MEM] init scopeKey={}", key);
        recordsByUser.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
    }

    @Override
    public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
        String key = scopeKey(context);
        if (limit <= 0 || !hasText(query)) {
            return List.of();
        }
        String normalizedQuery = normalize(query);
        Set<String> queryBigrams = bigrams(normalizedQuery);
        CopyOnWriteArrayList<MemoryRecord> scopedRecords =
                recordsByUser.getOrDefault(key, new CopyOnWriteArrayList<>());
        List<MemoryHit> hits = rankedHits(scopedRecords, normalizedQuery, queryBigrams, limit);
        LOG.info("[DEEP-RESEARCH-MEM] search scopeKey={} returned {} hits", key, hits.size());
        return hits;
    }

    @Override
    public void save(AgentExecutionContext context, List<MemoryRecord> records) {
        String key = scopeKey(context);
        if (records == null || records.isEmpty()) {
            return;
        }
        CopyOnWriteArrayList<MemoryRecord> scopedRecords =
                recordsByUser.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        for (MemoryRecord record : records) {
            if (record == null || !hasText(record.content())) {
                continue;
            }
            scopedRecords.add(stableRecord(record));
        }
    }

    public List<MemoryRecord> records(String tenantId, String userId) {
        return List.copyOf(recordsByUser.getOrDefault(scopeKey(tenantId, userId), new CopyOnWriteArrayList<>()));
    }

    private static MemoryHit toHit(MemoryRecord record, String normalizedQuery, Set<String> queryBigrams) {
        String normalizedContent = normalize(record.content());
        if (!normalizedQuery.isBlank() && normalizedContent.contains(normalizedQuery)) {
            return new MemoryHit(record.id(), record.content(), 1.0, record.metadata());
        }
        double score = bigramOverlapScore(queryBigrams, normalizedContent);
        return new MemoryHit(record.id(), record.content(), score, record.metadata());
    }

    private static double bigramOverlapScore(Set<String> queryBigrams, String normalizedContent) {
        if (queryBigrams.isEmpty()) {
            return 0.0;
        }
        int matched = 0;
        for (String bigram : queryBigrams) {
            if (normalizedContent.contains(bigram)) {
                matched++;
            }
        }
        return (double) matched / queryBigrams.size();
    }

    private static Set<String> bigrams(String text) {
        String stripped = text.replaceAll("\\s+", "");
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 2 <= stripped.length(); i++) {
            result.add(stripped.substring(i, i + 2));
        }
        return result;
    }

    private static List<MemoryHit> rankedHits(List<MemoryRecord> records, String normalizedQuery,
            Set<String> queryBigrams, int limit) {
        if (limit <= 0 || records == null || records.isEmpty()) {
            return List.of();
        }
        List<MemoryHit> candidates = new ArrayList<>();
        for (int index = records.size() - 1; index >= 0; index--) {
            MemoryRecord record = records.get(index);
            if (record == null || !hasText(record.content())) {
                continue;
            }
            candidates.add(toHit(record, normalizedQuery, queryBigrams));
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble(DeepResearchInMemoryMemoryProvider::scoreOrLowest).reversed())
                .limit(limit)
                .toList();
    }

    private static double scoreOrLowest(MemoryHit hit) {
        Double score = hit.score();
        return score == null ? Double.NEGATIVE_INFINITY : score;
    }

    private static MemoryRecord stableRecord(MemoryRecord record) {
        String id = hasText(record.id()) ? record.id() : UUID.randomUUID().toString();
        return new MemoryRecord(id, record.role(), record.content(), record.metadata());
    }

    private static String scopeKey(AgentExecutionContext context) {
        RuntimeIdentity scope = context.getScope();
        return scopeKey(scope.tenantId(), scope.userId());
    }

    private static String scopeKey(String tenantId, String userId) {
        String safeTenant = hasText(tenantId) ? tenantId : "default";
        String safeUser = hasText(userId) ? userId : "anonymous";
        return safeTenant + ":" + safeUser;
    }

    private static String normalize(String value) {
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
