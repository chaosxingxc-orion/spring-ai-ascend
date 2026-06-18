package com.huawei.ascend.a2a.memory.shared;

/**
 * Thrown by {@link BoundedSharedMemoryStore} when the shared-memory backend is at
 * capacity and a caller could not acquire an in-flight permit within the timeout.
 * This is the negative-feedback signal: under overload the kit sheds load rather
 * than piling unbounded concurrent calls onto the engine. Callers (or the
 * collaboration coordinator) treat it as a retryable, transient back-off.
 */
public final class BackpressureRejectedException extends RuntimeException {

    public BackpressureRejectedException(String op) {
        super("shared-memory backpressure: " + op + " rejected (backend at capacity)");
    }
}
