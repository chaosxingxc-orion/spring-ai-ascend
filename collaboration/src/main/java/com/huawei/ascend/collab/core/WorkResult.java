package com.huawei.ascend.collab.core;

/**
 * The outcome an agent returns for a dispatched sub-task. The worker MUST echo
 * the {@link TaskToken} it was given (the coordinator verifies it). For a
 * hand-over the worker returns {@link Status#HANDED_OVER} plus the capability it
 * is handing the task to.
 *
 * @param taskId             the sub-task id
 * @param status             outcome
 * @param output             result payload (validated by a {@link ResultValidator})
 * @param echoedToken        the token the worker received and is presenting back (may be null/wrong on a bad actor)
 * @param workerId           who produced this
 * @param handoverCapability when HANDED_OVER, the capability to hand to (else null)
 * @param detail             human-readable note (error message, etc.)
 */
public record WorkResult(
        String taskId,
        Status status,
        Object output,
        TaskToken echoedToken,
        String workerId,
        String handoverCapability,
        String detail) {

    public enum Status {
        COMPLETED,
        FAILED,
        TIMEOUT,
        HANDED_OVER,
        INPUT_REQUIRED,
        /** Set by the coordinator when the echoed token fails verification. */
        REJECTED
    }

    public static WorkResult completed(String taskId, Object output, TaskToken echoed, String workerId) {
        return new WorkResult(taskId, Status.COMPLETED, output, echoed, workerId, null, null);
    }

    public static WorkResult failed(String taskId, TaskToken echoed, String workerId, String detail) {
        return new WorkResult(taskId, Status.FAILED, null, echoed, workerId, null, detail);
    }

    public static WorkResult timeout(String taskId, TaskToken echoed, String workerId) {
        return new WorkResult(taskId, Status.TIMEOUT, null, echoed, workerId, null, "timeout");
    }

    public static WorkResult handedOver(String taskId, TaskToken echoed, String workerId, String toCapability) {
        return new WorkResult(taskId, Status.HANDED_OVER, null, echoed, workerId, toCapability, "hand-over");
    }
}
