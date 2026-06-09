package com.huawei.ascend.runtime.engine.agentscope;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.EngineInput;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import java.util.Map;
import java.util.Objects;

public final class AgentScopeMessageAdapter {

    public AgentScopeInvocation toInvocation(AgentExecutionContext context) {
        Objects.requireNonNull(context, "context");
        EngineExecutionScope scope = Objects.requireNonNull(context.getScope(), "scope");
        EngineInput input = context.getInput();
        return new AgentScopeInvocation(
                scope.tenantId(),
                scope.userId(),
                scope.sessionId(),
                scope.taskId(),
                scope.agentId(),
                input == null ? "USER_MESSAGE" : input.inputType(),
                input == null ? java.util.List.of() : input.messages(),
                input == null ? Map.of() : input.variables(),
                Map.of(
                        "tenantId", scope.tenantId(),
                        "userId", scope.userId() == null ? "" : scope.userId(),
                        "sessionId", scope.sessionId(),
                        "taskId", scope.taskId(),
                        "agentId", scope.agentId()));
    }
}
