package com.huawei.ascend.service.engine.api;

import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;

import java.util.Objects;

/**
 * Request to enqueue an Agent execution.
 *
 * <p>Execution closure: task-centric-control passes {@code agentId} in the scope,
 * which is used by EngineDispatcher to find the registered AgentHandler via
 * AgentHandlerRegistry.findByAgentId(scope.agentId), then execute the handler.
 *
 * <p>Constraints:
 * <ul>
 *   <li>External caller only carries {@code agentId} in the scope.</li>
 *   <li>External caller does not pass handler name.</li>
 *   <li>External caller does not pass underlying execution framework type.</li>
 *   <li>External caller does not pass openJiuwen internal execution mode.</li>
 *   <li>{@code scope.agentId} must match a registered value in AgentHandlerRegistry.</li>
 *   <li>If {@code agentId} is not found, EngineDispatcher generates EngineFailedEvent
 *       and writes back to task-centric-control.</li>
 * </ul>
 *
 * @param scope execution scope (task/session/agent context from task-centric-control).
 * @param input user input or system task input.
 */
public record EnqueueEngineExecutionRequest(
        EngineExecutionScope scope,
        EngineInput input) {

    public EnqueueEngineExecutionRequest {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(input, "input");
    }
}
