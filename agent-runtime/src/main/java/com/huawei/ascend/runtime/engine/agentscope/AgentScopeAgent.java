package com.huawei.ascend.runtime.engine.agentscope;

import java.util.stream.Stream;

@FunctionalInterface
public interface AgentScopeAgent {

    Stream<AgentScopeEvent> streamEvents(AgentScopeInvocation invocation);
}
