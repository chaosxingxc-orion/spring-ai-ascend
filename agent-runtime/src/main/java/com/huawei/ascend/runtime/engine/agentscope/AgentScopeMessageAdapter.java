package com.huawei.ascend.runtime.engine.agentscope;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.Map;
import java.util.Objects;

public final class AgentScopeMessageAdapter {

    public AgentScopeInvocation toInvocation(AgentExecutionContext context) {
        Objects.requireNonNull(context, "context");
        RuntimeIdentity scope = Objects.requireNonNull(context.getScope(), "scope");
        return new AgentScopeInvocation(
                scope.tenantId(), scope.userId(), scope.sessionId(),
                scope.taskId(), scope.agentId(),
                context.getInputType(), context.getMessages(), context.getVariables(),
                Map.of(
                        "tenantId", scope.tenantId(),
                        "userId", scope.userId() == null ? "" : scope.userId(),
                        "sessionId", scope.sessionId(),
                        "taskId", scope.taskId(),
                        "agentId", scope.agentId()));
    }
}
