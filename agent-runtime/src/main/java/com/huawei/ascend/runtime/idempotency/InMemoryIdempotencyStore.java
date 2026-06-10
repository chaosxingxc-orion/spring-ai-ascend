package com.huawei.ascend.runtime.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev-tier {@link IdempotencyStore}: a concurrent map keyed by
 * {@code (tenantId, key)} with atomic claim semantics — the same contract the
 * Postgres {@code idempotency_dedup} tier enforces with a unique constraint.
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Key(String tenantId, String key) {
    }

    private record Entry(boolean completed, String resultReference) {
    }

    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Claim claim(String tenantId, String key) {
        Entry existing = entries.putIfAbsent(new Key(tenantId, key), new Entry(false, null));
        if (existing == null) {
            return Claim.ACQUIRED;
        }
        return existing.completed() ? Claim.REPLAY : Claim.IN_FLIGHT;
    }

    @Override
    public void complete(String tenantId, String key, String resultReference) {
        entries.put(new Key(tenantId, key), new Entry(true, resultReference));
    }

    @Override
    public void release(String tenantId, String key) {
        entries.remove(new Key(tenantId, key));
    }

    @Override
    public Optional<String> completedReference(String tenantId, String key) {
        Entry entry = entries.get(new Key(tenantId, key));
        return entry != null && entry.completed()
                ? Optional.ofNullable(entry.resultReference()) : Optional.empty();
    }
}
