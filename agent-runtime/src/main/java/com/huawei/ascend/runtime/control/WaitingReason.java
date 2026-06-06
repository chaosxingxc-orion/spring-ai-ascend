package com.huawei.ascend.runtime.control;

/**
 * Structured reason attached to the single WAITING state.
 *
 * <p>The state machine stays compact while preserving the reason needed for
 * user-input, confirmation, or dependency waits.
 */
public enum WaitingReason {
    USER_INPUT,
    USER_CONFIRMATION,
    DEPENDENCY
}
