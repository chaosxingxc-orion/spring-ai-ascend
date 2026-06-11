package com.huawei.ascend.runtime.engine.agentscope;

import java.util.Objects;
import java.util.stream.Stream;

public final class AgentScopeHarnessRuntimeHandler extends AbstractAgentScopeRuntimeHandler {

    private final AgentScopeHarnessAgent harnessAgent;

    public AgentScopeHarnessRuntimeHandler(String agentId, AgentScopeHarnessAgent harnessAgent) {
        super(agentId, agentId, "AgentScope Harness agent hosted by agent-runtime.");
        this.harnessAgent = Objects.requireNonNull(harnessAgent, "harnessAgent");
    }

    public AgentScopeHarnessRuntimeHandler(
            String agentId,
            String name,
            String description,
            AgentScopeHarnessAgent harnessAgent) {
        super(agentId, name, description);
        this.harnessAgent = Objects.requireNonNull(harnessAgent, "harnessAgent");
    }

    @Override
    protected Stream<?> streamAgentScopeEvents(AgentScopeInvocation invocation) {
        return harnessAgent.streamEvents(invocation);
    }
}
