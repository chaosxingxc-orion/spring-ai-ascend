package com.huawei.ascend.runtime.engine.adapters.agentscope;

import java.util.stream.Stream;

@FunctionalInterface
public interface AgentScopeAgent {

    Stream<AgentScopeEvent> streamEvents(AgentScopeInvocation invocation);
}
