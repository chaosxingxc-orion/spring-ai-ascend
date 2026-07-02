package com.openjiuwen.runtime.alpha.planner;

import com.openjiuwen.core.alpha.model.*;
import com.openjiuwen.core.alpha.graph.TaskGraph;
import com.openjiuwen.core.kernel.model.TaskId;
import reactor.core.publisher.Mono;

/**
 * Planner 接口——PEV 第一阶段：把目标分解成 TaskGraph。
 *
 * 职责边界：
 * 1. 接收 PlanGoal，调用 LLM 生成 TaskGraph
 * 2. 校验 TaskGraph（环检测、依赖合理性、预算可行性）
 * 3. 校验失败时自纠错（最多 N 次，由 maxCorrectionRounds 控制）
 * 4. 根据 PlanningMode 触发审批流
 *
 * 不负责：
 * - 执行 TaskGraph（那是 Executor 的事）
 * - 验证执行结果（那是 Verifier 的事）
 *
 * 可替换性：开发者可以实现此接口替换默认的 LLM Planner。
 * 例如：
 * - 规则 Planner：不调 LLM，用规则引擎直接生成 TaskGraph
 * - 混合 Planner：简单任务用规则，复杂任务调 LLM
 */
public interface Planner {

    /**
     * 规划：把目标分解成 TaskGraph。
     *
     * 完整流程：
     * 1. 调用 LLM 生成 TaskGraph 的 JSON
     * 2. 解析 JSON → TaskGraph 对象
     * 3. 校验：环检测 + 依赖合理性 + 预算可行性
     * 4. 校验失败 → 将错误反馈给 LLM → 重新生成
     * 5. 校验通过 → 返回 PlanResult
     *
     * @param taskId 任务 ID（用于事件追踪）
     * @param goal   规划目标
     * @param policy 执行策略（决定是否需要审批等）
     * @return 规划结果
     */
    Mono<PlanResult> plan(TaskId taskId, PlanGoal goal, ExecutionPolicy policy);

    /**
     * GEPA-lite best-of-K 重规划：生成 K 个候选 plan + 确定性 fitness 选最优。
     *
     * <p>仅在 PlanOrAnswerError→GlobalReplan 路径（AlphaStrategy）且 {@code policy.bestOfKReplan()=true} 时调用。
     * failedVerify 携带上一次执行的失败节点 + 反馈，注入每个候选的 prompt（治 GlobalReplan 不喂 verify 上下文的缺陷）。
     * 候选多样性由实现侧的 prompt 突变轴提供（不触 runtime-core SPI）；fitness 须确定性信号（零 LLM-judge：
     * 感知层不可靠，故 fitness 用纯确定性函数，不引入 LLM-judge 这类不可靠感知层）。
     *
     * <p>默认实现（K=1 退化）：委托 {@link #plan}，忽略 failedVerify——保证非 DefaultPlanner 的实现（WealthGraphFactory +
     * 测试匿名类）零破坏。DefaultPlanner 覆写真实现。
     *
     * @param K            候选数（调用方 ExecutionPolicy 构造期已 clamp [1,3]）
     * @param failedVerify 上一次 verify 失败结果（提供 failedNodes/overallFeedback 进 prompt）
     * @return 选优后的 PlanResult（全部无效则返回失败 PlanResult，由调用方降级）
     */
    default Mono<PlanResult> planBestOfK(TaskId taskId, PlanGoal goal, ExecutionPolicy policy,
                                          int K, com.openjiuwen.core.alpha.verifier.VerifyResult failedVerify) {
        return plan(taskId, goal, policy);
    }

    /**
     * 校验 TaskGraph 的合法性。
     *
     * 校验项：
     * 1. DAG 环检测（Tarjan 算法）
     * 2. 节点依赖合理性（前置工具是否已注册？）
     * 3. 预算可行性（预估 Token 消耗 vs 预算上限）
     * 4. 约束检查（MaxSteps / RequiredTool）
     *
     * 此方法独立暴露，方便开发者在自定义 Planner 中复用校验逻辑。
     *
     * @param graph      待校验的 TaskGraph
     * @param goal       原始目标（用于合理性检查）
     * @param constraints 约束列表
     * @return 校验结果
     */
    PlanResult validate(TaskGraph graph, PlanGoal goal, java.util.List<Constraint> constraints);
}
