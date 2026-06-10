package com.huawei.ascend.runtime.idempotency;

import java.util.Optional;

/**
 * Tenant-scoped claim/replay store for idempotent request handling (W1
 * contract, ADR-0027): the first request claims its key and proceeds; a
 * retry of a completed request replays the recorded result reference; a
 * concurrent duplicate of an in-flight request is rejected by the caller.
 */
public interface IdempotencyStore {

    /** Outcome of attempting to claim a key. */
    enum Claim {
        /** First time seen — caller proceeds and must complete or release. */
        ACQUIRED,
        /** Same key is currently executing — caller must reject the duplicate. */
        IN_FLIGHT,
        /** Key completed earlier — caller replays via {@link #completedReference}. */
        REPLAY
    }

    Claim claim(String tenantId, String key);

    /** Records the result reference (e.g. the created taskId) and moves the key to REPLAY state. */
    void complete(String tenantId, String key, String resultReference);

    /** Releases a claim whose execution failed so the client may retry. */
    void release(String tenantId, String key);

    /** The recorded result reference for a completed key. */
    Optional<String> completedReference(String tenantId, String key);
}
