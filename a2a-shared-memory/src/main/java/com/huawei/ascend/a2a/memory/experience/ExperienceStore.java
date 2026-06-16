package com.huawei.ascend.a2a.memory.experience;

import java.util.List;

/**
 * Backend SPI for the cross-run experience layer. Keyed by
 * {@code tenantId + signature}; never by user. In-process
 * {@link InMemoryExperienceStore} for eval; a remote or persistent backend (e.g.
 * redis, or the MemOpt engine) may implement the same SPI for production — its
 * deployment is out of scope here. Implementations MUST isolate per tenant.
 * Lessons arrive already PII-stripped (the kit redacts before record).
 */
public interface ExperienceStore {

    /**
     * Persist distilled lessons under a tenant's signature. Re-recording an
     * identical lesson under the same signature should REFRESH it (recency +
     * reinforcement) rather than duplicate — that is how memory stays fresh.
     */
    void record(String tenantId, CollaborationSignature signature, List<Lesson> lessons);

    /**
     * Recall the most relevant lessons for a signature, freshest/most-useful first
     * (ranked by signature similarity, then reinforcement, then recency).
     */
    List<Lesson> recall(String tenantId, CollaborationSignature signature, int topK);

    /**
     * Usefulness feedback: a recalled lesson proved useful — reinforce it so it
     * ranks higher and survives eviction. Default no-op.
     */
    default void reinforce(String tenantId, CollaborationSignature signature, String lessonText) {
    }
}
