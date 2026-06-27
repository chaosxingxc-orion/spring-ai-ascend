package com.huawei.ascend.examples.workmate.team.backend;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Default {@link MemberBackend} that runs a member in-process via {@link AgentRunExecutor}.
 *
 * <p>Acts as the catch-all fallback (highest {@link #priority()}), so any member without a more
 * specific backend still runs locally.</p>
 */
@Component
public class LocalMemberBackend implements MemberBackend {

    private final AgentRunExecutor agentRunExecutor;

    public LocalMemberBackend(@Lazy AgentRunExecutor agentRunExecutor) {
        this.agentRunExecutor = agentRunExecutor;
    }

    @Override
    public String kind() {
        return "local";
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean supports(MemberDescriptor member) {
        return true;
    }

    @Override
    public MemberRunResult run(MemberRunContext context) {
        return MemberRunResult.from(agentRunExecutor.execute(context.request()));
    }
}
