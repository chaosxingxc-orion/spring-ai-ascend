package com.huawei.ascend.runtime.engine.agentscope;

import java.util.Objects;
import java.util.stream.Stream;

public final class AgentScopeRuntimeClientHandler extends AbstractAgentScopeRuntimeHandler {

    private final AgentScopeRuntimeClient client;

    public AgentScopeRuntimeClientHandler(String agentId, AgentScopeRuntimeClient client) {
        super(agentId, agentId, "AgentScope runtime client hosted by agent-runtime.");
        this.client = Objects.requireNonNull(client, "client");
    }

    public AgentScopeRuntimeClientHandler(
            String agentId,
            String name,
            String description,
            AgentScopeRuntimeClient client) {
        super(agentId, name, description);
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    protected Stream<?> streamAgentScopeEvents(AgentScopeInvocation invocation) {
        return client.streamEvents(invocation);
    }
}
