package com.huawei.ascend.a2a.memory.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * In-process {@link SharedMemoryStore} for offline eval and tests. Enforces the
 * ownership write rule and append-log semantics exactly as the closed engine
 * must, so the kit and the collaboration integration can be verified without the
 * gRPC engine. Thread-safe: per-collaboration state is concurrent and each key's
 * append is synchronized so ownership checks and version assignment are atomic.
 */
public final class InMemorySharedMemoryStore implements SharedMemoryStore {

    /** scope key = tenantId + '\0' + collaborationId → (blackboard key → append-log). */
    private final ConcurrentMap<String, ConcurrentMap<String, List<SharedEntry>>> store = new ConcurrentHashMap<>();
    /** scope key → (blackboard-key + idempotencyKey → the entry that write produced), for retry-safe
     *  writes. The blackboard key is part of the cache key so one request id reused across different
     *  keys (e.g. a multi-key write in a single dispatch) never collides and drops a write. */
    private final ConcurrentMap<String, ConcurrentMap<String, SharedEntry>> applied = new ConcurrentHashMap<>();
    /** scope key → interaction record (the team memory of who-did-what-to-whom). */
    private final ConcurrentMap<String, List<InteractionEntry>> interactions = new ConcurrentHashMap<>();
    /** scope key → (blackboard key → current owner agentId). Explicit so supersede can transfer it. */
    private final ConcurrentMap<String, ConcurrentMap<String, String>> owners = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public InMemorySharedMemoryStore() {
        this(System::currentTimeMillis);
    }

    public InMemorySharedMemoryStore(LongSupplier clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** Idempotency cache key — includes the blackboard key so reusing one request id
     *  across different keys never collides and drops a write (see {@link #applied}). */
    private static String idemCacheKey(String key, String idempotencyKey) {
        return key + "\0" + idempotencyKey;
    }

    private static String scope(String tenantId, String collaborationId) {
        return tenantId + "\0" + collaborationId;
    }

    @Override
    public SharedEntry append(String tenantId, String collaborationId, String key, String value, String writerAgentId,
            String idempotencyKey) {
        String scope = scope(tenantId, collaborationId);
        ConcurrentMap<String, List<SharedEntry>> board =
                store.computeIfAbsent(scope, k -> new ConcurrentHashMap<>());
        List<SharedEntry> log = board.computeIfAbsent(key, k -> new ArrayList<>());
        ConcurrentMap<String, SharedEntry> idem =
                applied.computeIfAbsent(scope, k -> new ConcurrentHashMap<>());
        // The append + idempotency record is atomic per key-log so a retried write
        // (same idempotencyKey) returns the prior entry and never double-appends.
        ConcurrentMap<String, String> owner = owners.computeIfAbsent(scope, k -> new ConcurrentHashMap<>());
        synchronized (log) {
            if (idempotencyKey != null) {
                SharedEntry prior = idem.get(idemCacheKey(key, idempotencyKey));
                if (prior != null) {
                    // A true retry repeats the same key+writer+value and returns the prior
                    // entry (no double-append). Reusing one idempotency id for a DIFFERENT
                    // payload would otherwise silently drop the new write, so reject it.
                    if (!prior.value().equals(value) || !prior.writerAgentId().equals(writerAgentId)) {
                        throw new IllegalStateException("idempotencyKey '" + idempotencyKey
                                + "' reused for key '" + key + "' with a different writer/value");
                    }
                    return prior;
                }
            }
            String currentOwner = owner.get(key);
            if (currentOwner != null && !currentOwner.equals(writerAgentId)) {
                throw new OwnershipViolationException(key, currentOwner, writerAgentId);
            }
            owner.putIfAbsent(key, writerAgentId);
            SharedEntry entry = new SharedEntry(key, value, writerAgentId, log.size() + 1, clock.getAsLong());
            log.add(entry);
            if (idempotencyKey != null) {
                idem.put(idemCacheKey(key, idempotencyKey), entry);
            }
            return entry;
        }
    }

    @Override
    public SharedEntry supersede(String tenantId, String collaborationId, String key, String value,
            String newWriterAgentId, String reason) {
        String scope = scope(tenantId, collaborationId);
        ConcurrentMap<String, List<SharedEntry>> board = store.computeIfAbsent(scope, k -> new ConcurrentHashMap<>());
        List<SharedEntry> log = board.computeIfAbsent(key, k -> new ArrayList<>());
        ConcurrentMap<String, String> owner = owners.computeIfAbsent(scope, k -> new ConcurrentHashMap<>());
        // Privileged override (e.g. the prior owner is unavailable): does NOT mutate
        // the prior owner's entries — appends a new version by newWriter and transfers
        // ownership going forward. History (provenance) is preserved.
        synchronized (log) {
            String prior = owner.get(key);
            owner.put(key, newWriterAgentId);
            SharedEntry entry = new SharedEntry(key, value, newWriterAgentId, log.size() + 1, clock.getAsLong());
            log.add(entry);
            recordInteraction(tenantId, collaborationId, new InteractionEntry(
                    InteractionEntry.InteractionType.VALIDATE, newWriterAgentId, prior == null ? "" : prior, key,
                    "superseded: " + (reason == null ? "" : reason), clock.getAsLong()));
            return entry;
        }
    }

    @Override
    public Optional<SharedEntry> latest(String tenantId, String collaborationId, String key) {
        List<SharedEntry> log = logOf(tenantId, collaborationId, key);
        if (log == null) {
            return Optional.empty();
        }
        synchronized (log) {
            return log.isEmpty() ? Optional.empty() : Optional.of(log.get(log.size() - 1));
        }
    }

    @Override
    public List<SharedEntry> history(String tenantId, String collaborationId, String key) {
        List<SharedEntry> log = logOf(tenantId, collaborationId, key);
        if (log == null) {
            return List.of();
        }
        synchronized (log) {
            return List.copyOf(log);
        }
    }

    @Override
    public List<String> keys(String tenantId, String collaborationId) {
        ConcurrentMap<String, List<SharedEntry>> board = store.get(scope(tenantId, collaborationId));
        return board == null ? List.of() : List.copyOf(board.keySet());
    }

    @Override
    public void recordInteraction(String tenantId, String collaborationId, InteractionEntry entry) {
        if (entry == null) {
            return;
        }
        List<InteractionEntry> log =
                interactions.computeIfAbsent(scope(tenantId, collaborationId), k -> new ArrayList<>());
        synchronized (log) {
            log.add(entry);
        }
    }

    @Override
    public List<InteractionEntry> interactions(String tenantId, String collaborationId) {
        List<InteractionEntry> log = interactions.get(scope(tenantId, collaborationId));
        if (log == null) {
            return List.of();
        }
        synchronized (log) {
            return List.copyOf(log);
        }
    }

    @Override
    public void release(String tenantId, String collaborationId) {
        String scope = scope(tenantId, collaborationId);
        store.remove(scope);
        applied.remove(scope);
        interactions.remove(scope);
        owners.remove(scope);
    }

    private List<SharedEntry> logOf(String tenantId, String collaborationId, String key) {
        ConcurrentMap<String, List<SharedEntry>> board = store.get(scope(tenantId, collaborationId));
        return board == null ? null : board.get(key);
    }
}
