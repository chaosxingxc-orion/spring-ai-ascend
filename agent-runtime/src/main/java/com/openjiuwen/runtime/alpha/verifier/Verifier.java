package com.openjiuwen.runtime.alpha.verifier;

import com.openjiuwen.core.alpha.graph.TaskGraph;
import com.openjiuwen.core.alpha.model.ExecutionPolicy;
import com.openjiuwen.core.alpha.model.PlanGoal;
import com.openjiuwen.core.alpha.verifier.ReplanStrategy;
import com.openjiuwen.core.alpha.verifier.VerifyResult;
import com.openjiuwen.core.kernel.model.BudgetLimits;
import com.openjiuwen.core.kernel.model.NodeId;
import com.openjiuwen.core.kernel.model.TaskId;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Verifier 接口——PEV 第三阶段：验证执行结果。
 *
 * 职责：
 * 1. 验证每个节点的输出是否满足 expectedOutput
 * 2. 验证 successCriteria 是否逐条满足
 * 3. 验证最终输出是否满足 OutputFormat 约束
 * 4. 验证失败时决定处理策略（局部 replan / 全局 replan / 放弃）
 *
 * 可插拔设计：
 * - 默认实现：DefaultVerifier（基于 LLM + 规则的混合验证）
 * - 开发者可以实现此接口替换验证逻辑
 * - 例如：基于规则的验证器（不调 LLM）、人工验证器（yield 等待人工确认）
 *
 * 验证层次：
 * - STRICT：逐节点验证 + successCriteria 逐条验证
 * - LIGHT：只验证最终输出是否满足目标
 * - NONE：跳过验证
 */
public interface Verifier {

    /**
     * 验证执行结果。
     *
     * @param taskId       任务 ID
     * @param goal         原始规划目标（包含 successCriteria）
     * @param graph        执行的 TaskGraph
     * @param nodeResults  节点 ID → 执行结果
     * @param policy       执行策略（决定验证严格程度）
     * @param budget       预算追踪
     * @return 验证结果
     */
    Mono<VerifyResult> verify(TaskId taskId, PlanGoal goal, TaskGraph graph,
                               Map<NodeId, Object> nodeResults,
                               ExecutionPolicy policy, BudgetLimits budget);

    /**
     * 决定验证失败后的处理策略。
     *
     * @param verifyResult 验证结果
     * @param retryCount   当前重试次数
     * @return 处理策略
     */
    ReplanStrategy decideReplanStrategy(VerifyResult verifyResult, int retryCount);
}
