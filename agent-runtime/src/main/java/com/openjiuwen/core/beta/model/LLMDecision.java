package com.openjiuwen.core.beta.model;

import com.openjiuwen.core.kernel.model.ToolName;

import java.util.List;
import java.util.Map;

/**
 * LLM 决策——Beta 策略中 LLM 自主做出的决策类型。
 *
 * sealed interface 确保所有决策类型都被显式处理。
 *
 * Replan 决策类型支持 LLM 在执行过程中推翻旧路径、切换到新策略。
 * 与 ContinueThinking 的区别：
 * - ContinueThinking = "我还在想，需要更多信息"
 * - Replan = "之前的路径走不通，我要换一条"
 */
public sealed interface LLMDecision
    permits LLMDecision.CallTool,
            LLMDecision.MultiCallTool,
            LLMDecision.ContinueThinking,
            LLMDecision.SpawnSubTasks,
            LLMDecision.RequestHumanHelp,
            LLMDecision.Replan,
            LLMDecision.Complete,
            LLMDecision.GiveUp {

    /**
     * 调用工具：LLM 决定调用一个工具。
     */
    record CallTool(
        ToolName toolName,
        Map<String, Object> arguments,
        String reasoning
    ) implements LLMDecision {}

    /**
     * 批量工具调用：并行执行多个独立工具。
     */
    record MultiCallTool(
        List<CallTool> tools,
        String reasoning
    ) implements LLMDecision {}

    /**
     * 继续思考：LLM 需要更多信息才能做决策。
     */
    record ContinueThinking(
        String thought,
        String nextQuestion
    ) implements LLMDecision {}

    /**
     * 生成子任务：LLM 判断当前目标太复杂，需要分解。
     */
    record SpawnSubTasks(
        List<GoalSpec> subGoals,
        String reasoning
    ) implements LLMDecision {}

    /**
     * 请求人类帮助：LLM 遇到不确定的情况，需要人类介入。
     */
    record RequestHumanHelp(
        String question,
        String context
    ) implements LLMDecision {}

    /**
     * 重规划：LLM 判断当前执行路径不可行，需要换一条路。
     *
     * 与 ContinueThinking 的区别：
     * - ContinueThinking 是"补充信息继续当前路径"
     * - Replan 是"放弃当前路径，换一条"
     */
    record Replan(
        String replanReason,
        String newApproach,
        String reasoning
    ) implements LLMDecision {}

    /**
     * 完成任务：LLM 认为目标已经达成。
     */
    record Complete(
        String output,
        double confidence,
        String summary
    ) implements LLMDecision {}

    /**
     * 放弃任务：LLM 判断无法完成目标。
     */
    record GiveUp(
        String reason,
        String partialResult
    ) implements LLMDecision {}
}
