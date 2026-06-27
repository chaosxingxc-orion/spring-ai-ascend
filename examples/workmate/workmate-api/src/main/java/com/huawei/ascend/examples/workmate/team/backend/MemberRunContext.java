package com.huawei.ascend.examples.workmate.team.backend;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteRequest;

/**
 * Everything a {@link MemberBackend} needs to run one member turn.
 *
 * <p>Carries the already-assembled {@link ExecuteRequest} (session, sub-run id, persistence
 * context, SSE emitter, flags) so the local backend can reuse the in-process executor, plus the
 * {@link MemberDescriptor} so remote / external backends can route by expert id and backend type.
 * Heterogeneous backends that do not use the in-process executor can read what they need from the
 * request (session, message, taskId) and ignore the rest.</p>
 *
 * @param member  the member being run
 * @param request the assembled in-process execute request
 */
public record MemberRunContext(MemberDescriptor member, ExecuteRequest request) {

    public MemberRunContext {
        if (member == null) {
            throw new IllegalArgumentException("member is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
    }

    public String memberExpertId() {
        return member.expertId();
    }
}
