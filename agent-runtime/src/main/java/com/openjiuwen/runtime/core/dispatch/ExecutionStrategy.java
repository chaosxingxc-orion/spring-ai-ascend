package com.openjiuwen.runtime.core.dispatch;

import com.openjiuwen.core.kernel.model.AgentEvent;
import com.openjiuwen.core.kernel.model.Checkpoint;
import reactor.core.publisher.Flux;

/**
 * 执行策略接口——调度层的核心抽象。
 *
 * <p>不同 AutonomyLevel 路由到不同策略实现：
 * <ul>
 *   <li>GUIDED/ASSISTED → PEVAlphaStrategy（PEV 显式控制）</li>
 *   <li>META/AUTONOMOUS  → Beta 自主编排</li>
 * </ul>
 *
 * <p>契约：execute() 返回的事件流必须以 TASK_COMPLETED 或 TASK_FAILED 终结。
 */
public interface ExecutionStrategy {

    /** 策略名称，用于路由和日志。如 "pev-alpha"。 */
    String name();

    /**
     * 执行任务——从零开始执行完整任务，返回事件流。
     *
     * @param context 任务上下文（输入、预算、工具列表等）
     * @return 事件流（TASK_STARTED → ... → TASK_COMPLETED/TASK_FAILED）
     */
    Flux<AgentEvent> execute(TaskContext context);

    /**
     * 从检查点恢复执行。
     *
     * @param context    任务上下文
     * @param checkpoint 恢复点
     * @return 事件流（从恢复点开始）
     */
    Flux<AgentEvent> resume(TaskContext context, Checkpoint checkpoint);
}
