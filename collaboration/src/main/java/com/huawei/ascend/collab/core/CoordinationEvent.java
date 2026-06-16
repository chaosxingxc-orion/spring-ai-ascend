package com.huawei.ascend.collab.core;

/**
 * One decision the coordinator made for a task — the audit/observability trail of
 * a collaboration, and what the eval harness scores against.
 */
public record CoordinationEvent(String taskId, Type type, String workerId, String detail) {

    public enum Type {
        DISPATCH,       // task handed to a worker (token issued)
        TOKEN_REJECT,   // worker's echoed token failed verification
        VALIDATE_OK,    // result passed validation
        VALIDATE_FAIL,  // result failed validation → reclaim
        HANDOVER,       // worker handed the task to another capability
        RECLAIM,        // coordinator reclaimed the task to retry/redispatch
        COMPLETE,       // task finished successfully
        FAIL,           // task gave up (attempts exhausted / no worker / rejected)
        NO_WORKER,      // no worker for the required capability
        INPUT_REQUIRED  // worker needs human input; batch can't resolve
    }

    static CoordinationEvent of(String taskId, Type type, String workerId, String detail) {
        return new CoordinationEvent(taskId, type, workerId, detail);
    }
}
