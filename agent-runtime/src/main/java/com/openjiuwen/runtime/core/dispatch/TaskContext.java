package com.openjiuwen.runtime.core.dispatch;

import com.openjiuwen.core.dispatch.AutonomyLevel;
import com.openjiuwen.core.kernel.model.*;
import com.openjiuwen.core.meta.AgentDefinition;
import com.openjiuwen.runtime.core.engine.AgentKernel;

import java.util.Map;

/**
 * 任务上下文——每次执行创建一个新的，不共享。
 *
 * <p>Agent 无状态设计保证：所有执行状态在 TaskContext 中，
 * 同一 Agent 可并发处理多个 Task，各有独立 TaskContext。
 */
public record TaskContext(
    TaskId taskId,
    AgentName agentName,
    TaskInput input,
    AgentDefinition agentDefinition,
    AgentKernel kernel,
    Budget budget,
    AutonomyLevel autonomyLevel,
    Map<String, Object> extraContext
) {
    /** 创建子任务上下文（继承 kernel 和 budget，使用新 taskId）。 */
    public TaskContext forSubTask(TaskId subTaskId, TaskInput subInput) {
        return new TaskContext(subTaskId, agentName, subInput, agentDefinition,
            kernel, budget, autonomyLevel, extraContext);
    }

    /** 获取当前预算追踪。 */
    public BudgetLimits currentBudgetLimits() {
        return BudgetLimits.start(budget);
    }
}
