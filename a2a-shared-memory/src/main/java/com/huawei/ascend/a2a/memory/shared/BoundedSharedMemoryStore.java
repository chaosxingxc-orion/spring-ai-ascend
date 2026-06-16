package com.huawei.ascend.a2a.memory.shared;

import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Backpressure decorator for any {@link SharedMemoryStore}: bounds the number of
 * concurrent in-flight operations with a permit semaphore. When the backend is at
 * capacity a caller waits up to {@code acquireTimeoutMs} for a permit; if none
 * frees up it is rejected with {@link BackpressureRejectedException} instead of
 * piling unbounded load onto the engine — the negative-feedback / load-shedding
 * the resilience requirement asks for under heavy traffic.
 *
 * <p>Rejections are counted ({@link #rejectedCount()}) and reported to the
 * {@link MemoryObserver} ({@code onDegraded(..., "backpressure-rejected")}) so ops
 * see saturation. The wrapped store keeps enforcing ownership / isolation; this
 * decorator only governs admission.
 */
public final class BoundedSharedMemoryStore implements SharedMemoryStore {

    private final SharedMemoryStore delegate;
    private final Semaphore permits;
    private final long acquireTimeoutMs;
    private final MemoryObserver observer;
    private final AtomicLong rejected = new AtomicLong();

    public BoundedSharedMemoryStore(SharedMemoryStore delegate, int maxConcurrent, long acquireTimeoutMs) {
        this(delegate, maxConcurrent, acquireTimeoutMs, MemoryObserver.NOOP);
    }

    public BoundedSharedMemoryStore(SharedMemoryStore delegate, int maxConcurrent, long acquireTimeoutMs,
            MemoryObserver observer) {
        this.delegate = delegate;
        this.permits = new Semaphore(Math.max(1, maxConcurrent), true); // fair: FIFO admission under load
        this.acquireTimeoutMs = Math.max(0, acquireTimeoutMs);
        this.observer = observer == null ? MemoryObserver.NOOP : observer;
    }

    /** Number of operations shed so far (saturation signal for ops). */
    public long rejectedCount() {
        return rejected.get();
    }

    @Override
    public SharedEntry append(String tenantId, String collaborationId, String key, String value, String writerAgentId,
            String idempotencyKey) {
        return guarded("append", tenantId,
                () -> delegate.append(tenantId, collaborationId, key, value, writerAgentId, idempotencyKey));
    }

    @Override
    public SharedEntry supersede(String tenantId, String collaborationId, String key, String value,
            String newWriterAgentId, String reason) {
        return guarded("supersede", tenantId,
                () -> delegate.supersede(tenantId, collaborationId, key, value, newWriterAgentId, reason));
    }

    @Override
    public Optional<SharedEntry> latest(String tenantId, String collaborationId, String key) {
        return guarded("latest", tenantId, () -> delegate.latest(tenantId, collaborationId, key));
    }

    @Override
    public List<SharedEntry> history(String tenantId, String collaborationId, String key) {
        return guarded("history", tenantId, () -> delegate.history(tenantId, collaborationId, key));
    }

    @Override
    public List<String> keys(String tenantId, String collaborationId) {
        return guarded("keys", tenantId, () -> delegate.keys(tenantId, collaborationId));
    }

    @Override
    public void recordInteraction(String tenantId, String collaborationId, InteractionEntry entry) {
        guarded("recordInteraction", tenantId, () -> {
            delegate.recordInteraction(tenantId, collaborationId, entry);
            return null;
        });
    }

    @Override
    public List<InteractionEntry> interactions(String tenantId, String collaborationId) {
        return guarded("interactions", tenantId, () -> delegate.interactions(tenantId, collaborationId));
    }

    @Override
    public void release(String tenantId, String collaborationId) {
        guarded("release", tenantId, () -> {
            delegate.release(tenantId, collaborationId);
            return null;
        });
    }

    private <T> T guarded(String op, String tenantId, java.util.function.Supplier<T> action) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackpressureRejectedException(op + " (interrupted)");
        }
        if (!acquired) {
            rejected.incrementAndGet();
            observer.onDegraded("shared." + op, tenantId, "backpressure-rejected");
            throw new BackpressureRejectedException(op);
        }
        try {
            return action.get();
        } finally {
            permits.release();
        }
    }
}
