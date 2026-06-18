package com.huawei.ascend.collab.core;

/**
 * One unit of work the coordinator distributes to a capable agent.
 *
 * @param id          stable sub-task id
 * @param capability  the capability required to handle it (used for routing)
 * @param payload     the work input (free-form; over A2A becomes the message text/metadata)
 * @param maxAttempts how many times the coordinator may (re)dispatch on failure/timeout before giving up
 * @param ttlMs       per-dispatch token time-to-live in ms (also the soft deadline)
 */
public record SubTask(String id, String capability, String payload, int maxAttempts, long ttlMs) {

    public SubTask {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("capability must not be blank");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
    }

    public static SubTask of(String id, String capability, String payload) {
        return new SubTask(id, capability, payload, 3, 30_000);
    }
}
