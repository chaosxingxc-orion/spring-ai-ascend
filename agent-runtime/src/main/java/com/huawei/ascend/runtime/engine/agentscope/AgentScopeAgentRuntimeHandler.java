package com.huawei.ascend.runtime.engine.agentscope;

import java.util.Objects;
import java.util.stream.Stream;

public final class AgentScopeAgentRuntimeHandler extends AbstractAgentScopeRuntimeHandler {

    private final AgentScopeAgent agent;

    public AgentScopeAgentRuntimeHandler(String agentId, AgentScopeAgent agent) {
        super(agentId, agentId, "AgentScope agent hosted by agent-runtime.");
        this.agent = Objects.requireNonNull(agent, "agent");
    }

    public AgentScopeAgentRuntimeHandler(String agentId, String name, String description, AgentScopeAgent agent) {
        super(agentId, name, description);
        this.agent = Objects.requireNonNull(agent, "agent");
    }

    @Override
    protected Stream<?> streamAgentScopeEvents(AgentScopeInvocation invocation) {
        return agent.streamEvents(invocation);
    }
}
