package com.huawei.ascend.examples.workmate.team.mailbox;

/**
 * Per-member-turn explicit handback state (local {@code send_message} to the team lead).
 *
 * <p>Replaces thread-local flags so local tool handbacks and A2A/backend implicit handbacks share
 * the same {@link com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool} lifecycle.</p>
 */
public final class MemberHandbackTurn {

    private final String summary;

    public MemberHandbackTurn(String summary) {
        this.summary = summary == null ? "" : summary.strip();
    }

    public String summary() {
        return summary;
    }
}
