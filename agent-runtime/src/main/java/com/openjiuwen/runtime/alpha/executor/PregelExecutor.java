package com.openjiuwen.runtime.alpha.executor;

import com.openjiuwen.core.alpha.executor.SuperstepResult;
import com.openjiuwen.core.alpha.graph.TaskGraph;
import com.openjiuwen.core.alpha.model.ExecutionPolicy;
import com.openjiuwen.core.kernel.model.BudgetLimits;
import com.openjiuwen.core.kernel.model.TaskId;
import reactor.core.publisher.Flux;

/**
 * Pregel BSP 执行器接口——PEV 第二阶段：执行 TaskGraph。
 *
 * <p>设计模型：Pregel BSP（Bulk Synchronous Parallel）
 * <ul>
 *   <li>超步（superstep）：同一层节点的并行执行</li>
 *   <li>同步屏障（barrier）：每层节点全部完成后才进入下一层</li>
 *   <li>消息传递：节点间通过 {@code ${nodeId.output}} 引用传递数据</li>
 * </ul>
 *
 * <p>可替换性：开发者可实现此接口替换执行器（如单线程调试器、分布式执行器）。
 */
public interface PregelExecutor {

    /**
     * 执行 TaskGraph。
     *
     * @param taskId 任务 ID
     * @param graph  待执行的 TaskGraph
     * @param policy 执行策略
     * @param budget 预算追踪（可变，执行过程中更新）
     * @return 超步结果流（每个超步产生一个 SuperstepResult）
     */
    Flux<SuperstepResult> execute(TaskId taskId, TaskGraph graph,
                                   ExecutionPolicy policy, BudgetLimits budget);
}
