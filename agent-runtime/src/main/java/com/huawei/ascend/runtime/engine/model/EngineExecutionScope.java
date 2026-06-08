package com.huawei.ascend.runtime.engine.model;

import java.util.Objects;

/**
 * Execution scope for an Agent run.
 *
 * <p>Represents the minimal business context required for one Agent execution.
 * The {@code agentId} is included in the scope and carried by task-centric-control
 * on a per-task basis.
 *
 * <p>First version does not design a separate run ID; async execution location
 * reuses {@code taskId} directly. If session-task-manager provides a unified
 * context type in the future, this class will be replaced by that real type.
 *
 * @param tenantId  tenant identifier (mandatory per Rule R-C.c).
 * @param userId    user identifier.
 * @param sessionId session identifier.
 * @param taskId    task identifier.
 * @param agentId   agent identifier (determines which handler to invoke).
 */
public record EngineExecutionScope(
        String tenantId,
        String userId,
        String sessionId,
        String taskId,
        String agentId) {

    public EngineExecutionScope {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(agentId, "agentId");
    }
}
