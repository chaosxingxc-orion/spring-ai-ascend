package com.huawei.ascend.engine.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-JVM side-channel carrying the RICH execution outcome (a thrown {@code Throwable} —
 * {@code SuspendSignal} for child-run / S2C suspension, {@code EngineMatchingException}, or
 * any runtime failure) out of the {@code EnginePort} event stream back to the Service driver,
 * keyed by an opaque handle. The neutral {@code AgentEvent} carries only the handle string;
 * the driver retrieves the {@code Throwable} in-JVM and rethrows it, preserving the existing
 * orchestrator suspend/resume + engine_mismatch control flow exactly.
 *
 * <p>The in-process port and the in-JVM mock transports share one instance, so the handle
 * resolves. A real cross-process transport does NOT move the Throwable over the wire — it
 * resolves suspension via the checkpoint-token protocol and reconstructs failure from the
 * FAILED event error-class.
 */
public final class EngineOutcomeChannel {

    private final ConcurrentHashMap<String, Throwable> outcomes = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public String put(Throwable outcome) {
        String handle = "oc-" + seq.incrementAndGet();
        outcomes.put(handle, outcome);
        return handle;
    }

    /** Retrieve and remove the outcome for the handle, or null if absent (e.g. crossed a real wire). */
    public Throwable take(String handle) {
        return handle == null ? null : outcomes.remove(handle);
    }
}
