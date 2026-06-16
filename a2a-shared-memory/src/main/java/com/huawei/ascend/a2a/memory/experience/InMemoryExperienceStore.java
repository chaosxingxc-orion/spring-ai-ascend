package com.huawei.ascend.a2a.memory.experience;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-process {@link ExperienceStore} for offline eval and tests, with freshness:
 *
 * <ul>
 *   <li><b>dedup-refresh</b> — re-recording an identical (signature, text) lesson
 *       bumps its reinforcement and recency instead of duplicating;</li>
 *   <li><b>usefulness feedback</b> — {@link #reinforce} bumps a lesson that proved
 *       useful on recall;</li>
 *   <li><b>freshness-ranked recall</b> — by signature similarity, then reinforcement,
 *       then recency;</li>
 *   <li><b>bounded eviction</b> — capped per tenant; the lowest (reinforcement,
 *       then oldest) record is evicted so stale, unreinforced lessons decay out.</li>
 * </ul>
 *
 * Tenant isolation is enforced by the per-tenant partition key.
 */
public final class InMemoryExperienceStore implements ExperienceStore {

    private static final class Record {
        final CollaborationSignature signature;
        Lesson lesson;

        Record(CollaborationSignature signature, Lesson lesson) {
            this.signature = signature;
            this.lesson = lesson;
        }
    }

    private final ConcurrentMap<String, List<Record>> byTenant = new ConcurrentHashMap<>();
    private final int maxRecordsPerTenant;

    public InMemoryExperienceStore() {
        this(500);
    }

    public InMemoryExperienceStore(int maxRecordsPerTenant) {
        this.maxRecordsPerTenant = Math.max(1, maxRecordsPerTenant);
    }

    @Override
    public void record(String tenantId, CollaborationSignature signature, List<Lesson> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return;
        }
        List<Record> records = byTenant.computeIfAbsent(tenantId, k -> new ArrayList<>());
        synchronized (records) {
            for (Lesson lesson : lessons) {
                if (lesson == null || lesson.text() == null || lesson.text().isBlank()) {
                    continue;
                }
                Record existing = find(records, signature, lesson.text());
                if (existing != null) {
                    existing.lesson = existing.lesson.refreshed(lesson.tsEpochMs()); // dedup-refresh
                } else {
                    records.add(new Record(signature, lesson));
                }
            }
            evict(records);
        }
    }

    @Override
    public void reinforce(String tenantId, CollaborationSignature signature, String lessonText) {
        List<Record> records = byTenant.get(tenantId);
        if (records == null || lessonText == null) {
            return;
        }
        synchronized (records) {
            Record existing = find(records, signature, lessonText);
            if (existing != null) {
                existing.lesson = existing.lesson.refreshed(existing.lesson.tsEpochMs());
            }
        }
    }

    @Override
    public List<Lesson> recall(String tenantId, CollaborationSignature signature, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        List<Record> records = byTenant.get(tenantId);
        if (records == null) {
            return List.of();
        }
        List<Record> snapshot;
        synchronized (records) {
            snapshot = new ArrayList<>(records);
        }
        snapshot.sort(Comparator
                .comparingDouble((Record r) -> r.signature.similarity(signature))
                .thenComparingInt((Record r) -> r.lesson.reinforcement())
                .thenComparingLong((Record r) -> r.lesson.tsEpochMs())
                .reversed());
        List<Lesson> out = new ArrayList<>();
        for (Record r : snapshot) {
            if (r.signature.similarity(signature) <= 0.0) {
                break; // ranked desc — nothing relevant left
            }
            out.add(r.lesson);
            if (out.size() >= topK) {
                break;
            }
        }
        return out;
    }

    private static Record find(List<Record> records, CollaborationSignature signature, String text) {
        for (Record r : records) {
            if (r.signature.equals(signature) && r.lesson.text().equals(text)) {
                return r;
            }
        }
        return null;
    }

    /** Evict lowest (reinforcement, then oldest) until within cap — stale lessons decay out. */
    private void evict(List<Record> records) {
        while (records.size() > maxRecordsPerTenant) {
            int worst = 0;
            for (int i = 1; i < records.size(); i++) {
                Lesson a = records.get(i).lesson;
                Lesson b = records.get(worst).lesson;
                if (a.reinforcement() < b.reinforcement()
                        || (a.reinforcement() == b.reinforcement() && a.tsEpochMs() < b.tsEpochMs())) {
                    worst = i;
                }
            }
            records.remove(worst);
        }
    }
}
