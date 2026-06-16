package com.huawei.ascend.runtime.engine.spi;

/**
 * Write-only store for oversized trajectory payloads. When a payload string exceeds the
 * configured {@code truncateChars} threshold, the runtime calls {@link #put} to persist the
 * content externally and records an opaque reference in the event instead of a truncated excerpt.
 * The reference format is implementation-defined (e.g. {@code payload_ref://sha256}).
 *
 * <p><strong>Architecture invariant:</strong> the runtime is write-only — it calls {@code put}
 * and discards the handle. Payload retrieval is the external consumer's responsibility. This
 * keeps the agent runtime strictly emit-only and free of any read-back or projection logic.
 *
 * <p>Implementations MUST be thread-safe. The runtime calls {@code put} on the emit thread
 * (inside {@code synchronized emit()}); a blocking implementation adds latency to runs and
 * should be avoided. Prefer local-FS or async-write backends. Never throw checked exceptions —
 * if a write fails, throw an unchecked exception; the emitter falls back to normal truncation.
 */
@FunctionalInterface
public interface PayloadRefStore {

    /**
     * Persists {@code payload} and returns an opaque, non-null reference string. The reference
     * is recorded verbatim in the trajectory event's {@code payload_ref} field.
     *
     * @param payload the full payload string to persist (never null)
     * @return a non-null opaque reference the consumer can use to retrieve the payload
     * @throws RuntimeException if the write fails; the emitter falls back to truncation
     */
    String put(String payload);
}
