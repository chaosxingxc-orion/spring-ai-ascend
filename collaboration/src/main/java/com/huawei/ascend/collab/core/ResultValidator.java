package com.huawei.ascend.collab.core;

/**
 * Validates a completed sub-task result (the "校验" step). A failed validation
 * makes the coordinator reclaim the task and retry/redispatch, so a worker can't
 * close a task with a wrong/empty answer.
 */
@FunctionalInterface
public interface ResultValidator {

    boolean isValid(SubTask task, WorkResult result);

    /** Default: a completed result must carry a non-empty output. */
    static ResultValidator nonEmptyOutput() {
        return (task, result) -> {
            Object o = result.output();
            return o != null && !String.valueOf(o).isBlank();
        };
    }

    /** Accept anything (use when validation lives inside the worker). */
    static ResultValidator acceptAll() {
        return (task, result) -> true;
    }
}
