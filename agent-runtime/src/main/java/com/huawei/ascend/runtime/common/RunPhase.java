package com.huawei.ascend.runtime.common;

/**
 * Lifecycle phase of a run, carried on each {@link RunEvent}. Terminal phases are SUCCEEDED,
 * FAILED and CANCELLED; WAITING_INPUT is a non-terminal suspend awaiting caller input.
 */
public enum RunPhase {
    PENDING,
    RUNNING,
    WAITING_INPUT,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
