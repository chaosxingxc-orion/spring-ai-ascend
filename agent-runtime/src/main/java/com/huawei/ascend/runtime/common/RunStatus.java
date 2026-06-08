package com.huawei.ascend.runtime.common;

/**
 * Canonical run lifecycle status, aligned with the agentscope-runtime
 * {@code RunStatus} vocabulary.
 *
 * <p>This is the user-facing / cross-module lifecycle view of a run. The
 * task-control layer keeps its own finer-grained internal {@code TaskState}
 * (e.g. {@code CREATED}, {@code CANCELLING}); this enum is what the access
 * layer surfaces to callers and what flows on the standard
 * {@link AgentResponseEvent}.
 */
public enum RunStatus {
    CREATED,
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    INCOMPLETE,
    FAILED,
    CANCELED,
    REJECTED,
    UNKNOWN;

    /** True once the run has reached a state that will not change again. */
    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, FAILED, CANCELED, REJECTED, INCOMPLETE -> true;
            default -> false;
        };
    }

    /** The lower-case wire form used on the standard response object. */
    public String wire() {
        return name().toLowerCase();
    }
}
