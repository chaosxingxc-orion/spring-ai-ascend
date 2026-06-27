package com.huawei.ascend.examples.workmate.team.backend;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome;

/**
 * Result of running a member turn through a {@link MemberBackend}.
 *
 * @param assistantText the member's textual output
 * @param failed        whether the run failed
 * @param errorMessage  failure detail (may be {@code null} when {@code failed} is false)
 * @param backendKind   {@link MemberBackend#kind()} that produced this result (e.g. {@code local}, {@code a2a})
 */
public record MemberRunResult(String assistantText, boolean failed, String errorMessage, String backendKind) {

    public MemberRunResult {
        assistantText = assistantText == null ? "" : assistantText;
        backendKind = backendKind == null || backendKind.isBlank() ? "local" : backendKind.strip();
    }

    public static MemberRunResult ok(String assistantText) {
        return ok(assistantText, "local");
    }

    public static MemberRunResult ok(String assistantText, String backendKind) {
        return new MemberRunResult(assistantText, false, null, backendKind);
    }

    public static MemberRunResult failed(String errorMessage) {
        return new MemberRunResult("", true, errorMessage, "local");
    }

    /** Adapt the existing {@link ExecuteOutcome} so backends can reuse the in-process executor. */
    public static MemberRunResult from(ExecuteOutcome outcome) {
        if (outcome == null) {
            return failed("no outcome");
        }
        return new MemberRunResult(outcome.assistantText(), outcome.failed(), outcome.errorMessage(), "local");
    }
}
