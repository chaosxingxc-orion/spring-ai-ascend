package com.openjiuwen.runtime.core.engine;

import com.openjiuwen.core.kernel.model.*;
import com.openjiuwen.core.meta.AgentDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Agent 内核——7 个系统调用。
 *
 * 这是 Runtime 最核心的抽象。所有策略（Alpha/Beta）最终都通过这 7 个系统调用
 * 与底层能力交互。AgentKernel 是唯一的"特权层"，可以：
 * - 调用 LLM（通过 Spring AI ChatModel）
 * - 调用工具（通过 ToolRegistry / MCP）
 * - 读写检查点
 * - 访问虚拟文件系统
 *
 * 策略层不能直接调 LLM 或工具——必须通过 AgentKernel。
 * 这确保了 SafetyBoundary 在每个操作前后都能执行检查。
 *
 * 契约：
 * - 所有方法都是无状态的，通过参数传入上下文
 * - 所有方法返回 Mono/Flux，不阻塞
 * - 每个方法调用前后都会经过 SafetyBoundary 检查
 */
public interface AgentKernel {

    // ==================== 系统调用 1: 思考 ====================

    /**
     * 调用 LLM 进行推理。
     *
     * 语义：将 prompt（含对话历史）发送给 LLM，返回 LLM 的回复。
     * 用途：Alpha 策略的规划/验证阶段、Beta 策略的自主决策。
     *
     * @param prompt    输入提示（包含系统提示、对话历史、当前观察）
     * @param budget    当前预算追踪
     * @return LLM 的文本回复
     */
    Mono<String> think(String prompt, BudgetLimits budget);

    /**
     * 调用 LLM 进行推理（带身份，可发流式 chunk 事件）。
     *
     * <p>语义同 {@link #think(String, BudgetLimits)}，但携带 {@code taskId}/{@code nodeId}，
     * 使内核能在流式 decode 过程中发布 {@link EventType#THINKING_BLOCK_START}/
     * {@link EventType#THINKING_DELTA}/{@link EventType#THINKING_BLOCK_END} 三段式事件
     * （对齐 AgentScope v2 {@code *_BLOCK_START/_DELTA/_END}）。前端据此显示"正在思考"进度，
     * 扇出并行节点的 chunk 按 BLOCK_START 的 nodeId 分桶。
     *
     * <p>默认实现退化为不发声事件的 {@link #think(String, BudgetLimits)}——兼容测试/mock 及无 taskId 场景。
     *
     * @param taskId 任务 ID（null 时不发流式事件）
     * @param nodeId 归属节点 ID（null 时为规划/验证等无 node 阶段）
     */
    default Mono<String> think(String prompt, BudgetLimits budget, TaskId taskId, NodeId nodeId) {
        return think(prompt, budget);
    }

    // ==================== 系统调用 2: 工具调用 ====================

    /**
     * 调用一个已注册的工具。
     *
     * 语义：执行 @Tool 方法或 MCP 远程工具，返回结果。
     * 用途：Alpha 策略的叶子节点执行、Beta 策略的工具调用决策。
     *
     * @param toolName  工具名称
     * @param arguments 工具参数
     * @param budget    当前预算追踪
     * @return 工具执行结果
     */
    Mono<ToolResult> invokeTool(ToolName toolName, Map<String, Object> arguments, BudgetLimits budget);

    /**
     * 列出已注册工具的签名（名称 + 参数 schema）。
     *
     * <p>语义：返回 kernel 当前持有的全部 {@link AgentDefinition.ToolDefinition}（由
     * {@code AgentBeanPostProcessor} 在 @Tool 注册时一并登记）。与 {@link #invokeTool} 互补——
     * invokeTool 执行工具，本方法暴露工具的<b>调用契约</b>（参数名 / 类型 / 必填），供 Planner 向 LLM
     * 渲染准确的工具签名（而非裸工具名），避免 LLM 生成的 TOOL_CALL inputs key 与 @Tool 参数名不符（工具签名透传）。
     *
     * <p>默认返回空列表——兼容无 schema 的 mock / 测试实现；Planner 对无匹配签名的工具名回退渲染裸名。
     *
     * @return 不可变工具签名列表（可能为空）
     */
    default List<AgentDefinition.ToolDefinition> getToolDefinitions() {
        return List.of();
    }

    // ==================== 系统调用 3: 观察 ====================

    /**
     * 观察当前执行状态。
     *
     * 语义：查询任务图执行进度、中间结果、已完成的节点。
     * 用途：Alpha 策略的节点间数据传递、Beta 策略的环境感知。
     *
     * @param taskId    任务 ID
     * @param nodeIds   要观察的节点 ID 列表（空 = 观察全部）
     * @return 节点 ID → 结果的映射
     */
    Mono<Map<NodeId, Object>> observe(TaskId taskId, java.util.Set<NodeId> nodeIds);

    // ==================== 系统调用 4: 检查点保存 ====================

    /**
     * 保存检查点。
     *
     * 语义：将当前执行状态序列化并持久化。
     * 用途：每层执行完成后自动保存、策略主动保存。
     *
     * @param checkpoint 检查点数据
     * @return 保存确认
     */
    Mono<CheckpointId> saveCheckpoint(Checkpoint checkpoint);

    // ==================== 系统调用 5: 检查点恢复 ====================

    /**
     * 从检查点恢复执行状态。
     *
     * 语义：加载最近的检查点，反序列化执行状态。
     * 用途：崩溃恢复、长流程暂停后恢复。
     *
     * @param taskId 任务 ID
     * @return 最近的检查点，如果没有则返回 empty
     */
    Mono<Checkpoint> restoreCheckpoint(TaskId taskId);

    // ==================== 系统调用 6: Yield ====================

    /**
     * 让出执行权。
     *
     * 语义：Agent 主动暂停，保存检查点，等待外部输入。
     * 用途：等待人工审批、等待异步操作完成。
     *
     * @param taskId      任务 ID
     * @param reason      让出原因
     * @param currentState 当前执行状态的 JSON 快照
     * @return 保存的检查点 ID
     */
    Mono<CheckpointId> yield(TaskId taskId, YieldReason reason, String currentState);

    // ==================== 系统调用 7: 事件发布 ====================

    /**
     * 发布事件。
     *
     * 语义：将 Agent 的行为事件推送给订阅者（SDK、前端、审计系统）。
     * 用途：实时展示 Agent 推理过程、工具调用过程、执行进度。
     *
     * @param event Agent 事件
     * @return 发布确认
     */
    Mono<Void> emit(AgentEvent event);

    /**
     * 订阅任务的所有事件流。
     *
     * @param taskId 任务 ID
     * @return 事件流（冷源，每次 subscribe 从头开始）
     */
    Flux<AgentEvent> observeEvents(TaskId taskId);
}
