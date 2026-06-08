package com.huawei.ascend.runtime.control;

/**
 * Task-Centric Control (TCC) visible failure classes.
 *
 * <p>Runtime-specific errors are normalized before they cross into task control;
 * the queue layer still treats the containing object as opaque payload.
 */
public enum TaskFailureCode {
    AGENT_ID_INVALID,
    OUT_OF_DOMAIN,
    NOT_CURRENT_TASK,
    ENGINE_DISPATCH_REJECTED,
    RUNTIME_ERROR,
    CANCELLED_BY_RUNTIME
}
