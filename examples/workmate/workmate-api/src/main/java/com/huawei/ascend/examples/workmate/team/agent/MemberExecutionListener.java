package com.huawei.ascend.examples.workmate.team.agent;

/**
 * Callbacks when a TeamAgent member DeepAgent runs via {@code send_message} / {@code runMember}.
 */
@FunctionalInterface
public interface MemberExecutionListener {

    MemberExecutionListener NOOP = (event, memberId, detail) -> {};

    void onMemberExecution(MemberExecutionEvent event, String memberId, String detail);

    enum MemberExecutionEvent {
        STARTED,
        COMPLETED,
        FAILED
    }
}
