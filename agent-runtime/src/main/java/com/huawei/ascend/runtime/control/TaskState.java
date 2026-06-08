package com.huawei.ascend.runtime.control;

/**
 * Task-Centric Control (TCC) owned main Task state set.
 *
 * <p>WAITING_FOR_TOOL and EXPIRED are intentionally not primary states here:
 * tool waits are represented by detail/reason data, and expiry is reported as a
 * runtime failure/detail.
 */
public enum TaskState {
    CREATED,
    RUNNING,
    WAITING,
    PAUSED,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
