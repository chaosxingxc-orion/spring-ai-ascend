package com.huawei.ascend.a2a.memory.shared;

/**
 * Thrown when an agent tries to write a blackboard key owned by another agent.
 * A key's owner is the agent that first wrote it; only the owner may update it
 * (after hand-over A&rarr;B, B writes its own keys, A's stay immutable to B).
 * A remote backend maps this to its transport's permission-denied error.
 */
public final class OwnershipViolationException extends RuntimeException {

    public OwnershipViolationException(String key, String owner, String attemptedWriter) {
        super("key '" + key + "' is owned by '" + owner + "'; '" + attemptedWriter
                + "' may not write it (read-only to non-owners)");
    }
}
