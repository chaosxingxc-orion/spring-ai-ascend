package com.huawei.ascend.runtime.engine.service;

import com.huawei.ascend.runtime.common.Guards;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import java.util.Objects;

/**
 * Identifies the runtime-owned Agent state for one accepted task.
 *
 * <p>The key deliberately includes {@code taskId}: Agent state is used to resume
 * an interrupted task, not to store long-lived business state for every future
 * task in the same session.
 */
public record AgentStateKey(
        String tenantId,
        String userId,
        String sessionId,
        String taskId,
        String agentId) {

    public AgentStateKey {
        tenantId = Guards.requireNonBlank(tenantId, "tenantId");
        sessionId = Guards.requireNonBlank(sessionId, "sessionId");
        taskId = Guards.requireNonBlank(taskId, "taskId");
        agentId = Guards.requireNonBlank(agentId, "agentId");
    }

    public static AgentStateKey from(EngineExecutionScope scope) {
        Objects.requireNonNull(scope, "scope");
        return new AgentStateKey(scope.tenantId(), scope.userId(), scope.sessionId(), scope.taskId(), scope.agentId());
    }
}
