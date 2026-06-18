/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
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
 * Process-local {@link MemoryProvider} for the hotel A2A demo.
 *
 * <p>Records are scoped by {@code tenantId:userId} rather than the runtime's default
 * {@code agentStateKey} (which is {@code taskId}). The user-facing scenario — "save a
 * booking, recall it on a later question" — happens across A2A sessions/tasks, so the
 * partition must span tasks.
 *
 * <p>Similarity is scored over character bigrams of the normalized query against
 * the normalized record content. Whitespace-tokenized word overlap (the upstream
 * example's scorer) does not work for Chinese where there are no word boundaries,
 * so a paraphrased question like 「上次在北京住的是哪家」 would not recall a saved
 * 「已为您预订北京 BJ-001 酒店」. Character bigrams catch the shared 「北京」/「酒店」/「住」
 * pairs without a Chinese-aware tokenizer.
 *
 * <p>This in-memory demo keeps low-score records as fallback candidates instead
 * of producing an empty recall, because it has no embedding index, reranker, or
 * production threshold tuning. External memory backends should keep their own
 * threshold and ranking policy; this fallback is intentionally scoped to the
 * simplest process-local example. Higher scores still rank first, and equal
 * scores keep the most recent records first.
 *
 * <p>This is intentionally NOT a production memory backend: storage is lost on
 * process restart, and scoring is text-only with no embeddings.
 */
public final class HotelInMemoryMemoryProvider implements MemoryProvider {

    private static final Logger LOG = LoggerFactory.getLogger(HotelInMemoryMemoryProvider.class);

    private final ConcurrentMap<String, CopyOnWriteArrayList<MemoryRecord>> recordsByUser =
            new ConcurrentHashMap<>();

    @Override
    public void init(AgentExecutionContext context) {
        String key = scopeKey(context);
        // The MemoryRuntimeRail calls injectMemory(callbackContext) immediately after init();
        // it early-returns silently when executionContext.lastUserText() is blank. We log
        // the current lastUserText and message-list size on every init so we can see why
        // search() never fires when recall fails end-to-end.
        String lastUserText = context.getMessages() == null ? "<null>" : context.getMessages().stream()
                .filter(m -> m.role() == RuntimeMessage.Role.USER)
                .map(RuntimeMessage::text)
                .reduce((first, second) -> second)
                .orElse("<no-user-msg>");
        LOG.info("[HOTEL-MEM] init scopeKey={} messageCount={} lastUserText={}",
                key,
                context.getMessages() == null ? 0 : context.getMessages().size(),
                lastUserText);
        recordsByUser.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
    }

    @Override
    public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
        String key = scopeKey(context);
        LOG.info("[HOTEL-MEM] search scopeKey={} limit={} query={}", key, limit, query);
        if (limit <= 0 || !hasText(query)) {
            return List.of();
        }
        String normalizedQuery = normalize(query);
        Set<String> queryBigrams = bigrams(normalizedQuery);
        CopyOnWriteArrayList<MemoryRecord> scopedRecords =
                recordsByUser.getOrDefault(key, new CopyOnWriteArrayList<>());
        List<MemoryHit> hits = rankedHits(scopedRecords, normalizedQuery, queryBigrams, limit);
        LOG.info("[HOTEL-MEM] search scopeKey={} returned {} hits", key, hits.size());
        return hits;
    }

    @Override
    public void save(AgentExecutionContext context, List<MemoryRecord> records) {
        String key = scopeKey(context);
        LOG.info("[HOTEL-MEM] save scopeKey={} incomingRecords={}", key,
                records == null ? 0 : records.size());
        if (records == null || records.isEmpty()) {
            return;
        }
        CopyOnWriteArrayList<MemoryRecord> scopedRecords =
                recordsByUser.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        int kept = 0;
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            String stripped = stripRecallBlock(record.content());
            if (!hasText(stripped)) {
                continue;
            }
            MemoryRecord cleaned = new MemoryRecord(record.id(), record.role(), stripped, record.metadata());
            scopedRecords.add(stableRecord(cleaned));
            kept++;
        }
        LOG.info("[HOTEL-MEM] save scopeKey={} stored={} total={}", key, kept, scopedRecords.size());
    }

    /**
     * Snapshot of the records saved for one {@code (tenantId, userId)} pair. Used
     * by {@link HotelMemoryDebugController} so operators can see what landed in
     * memory during the demo. Returns an empty list when nothing has been saved
     * yet; never throws.
     */
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
                .sorted(Comparator.comparingDouble(HotelInMemoryMemoryProvider::scoreOrLowest).reversed())
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

    /**
     * Defensive removal of any {@code Relevant memory: ... ---} block the
     * handler may have prepended to the saved user message. The handler is
     * expected to save the original (un-prepended) query, so in practice this
     * is a safety net for surprise call paths or for assistant replies that
     * echo the block back.
     */
    private static String stripRecallBlock(String content) {
        if (content == null) {
            return "";
        }
        int openIdx = content.indexOf("Relevant memory:");
        if (openIdx < 0) {
            return content;
        }
        int closeIdx = content.indexOf("\n---\n", openIdx);
        if (closeIdx < 0) {
            return content;
        }
        String before = content.substring(0, openIdx);
        String after = content.substring(closeIdx + "\n---\n".length());
        return (before + after).trim();
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
