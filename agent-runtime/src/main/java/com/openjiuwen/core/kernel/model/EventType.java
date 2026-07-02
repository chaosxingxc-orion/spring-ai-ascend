package com.openjiuwen.core.kernel.model;

/**
 * 事件类型枚举。
 *
 * 三组事件：通用事件 + Alpha（PEV）策略事件 + Beta（LLM 自主编排）策略事件。
 */
public enum EventType {

    // --- 通用事件 ---
    TASK_CREATED,       // 任务已创建
    TASK_STARTED,       // 任务开始执行
    THINKING,           // Agent 思考过程（粗粒度占位，生产未 emit）
    THINKING_BLOCK_START, // LLM 思考块开始（流式三段式：块头带 nodeId，前端按块分桶）
    THINKING_DELTA,     // LLM 思考块增量 chunk（逐 token 文本）
    THINKING_BLOCK_END, // LLM 思考块结束
    TOOL_CALL,          // 工具调用开始
    TOOL_RESULT,        // 工具调用结果
    CHECKPOINT_SAVED,   // 检查点已保存
    TASK_COMPLETED,     // 任务完成
    TASK_FAILED,        // 任务失败
    TASK_PAUSED,        // 任务暂停
    TASK_CANCELLED,     // 任务取消
    PLACEHOLDER_SANITIZED, // 残留占位符已剥离（检测到未解析 ${...} 并净化，不中断执行）

    // --- Alpha 策略专属 ---
    PLAN_GENERATED,     // TaskGraph 规划完成
    PLAN_REVISED,       // TaskGraph 局部修订
    PLAN_PARSE_ERROR,   // 规划解析失败（LLM 响应无法解析为 TaskGraph；携带 issue.code + 响应长度/摘要）
    PLAN_RETRY,         // 规划自纠错重试（携带 round/maxRounds + 触发本轮的失败 code）
    NODE_STARTED,       // 任务图节点开始执行
    NODE_COMPLETED,     // 任务图节点执行完成
    NODE_FAILED,        // 任务图节点执行失败
    LAYER_COMPLETED,    // 一层节点全部执行完成
    VERIFY_PASSED,      // 验证通过
    VERIFY_FAILED,      // 验证失败
    ROOT_CAUSE_DIAGNOSED, // verify 失败根因诊断（根因驱动自愈：设备故障/感知出错/图-答案出错）
    APPROVAL_REQUIRED,  // 需要人工审批
    APPROVAL_GRANTED,   // 审批通过
    APPROVAL_DENIED,    // 审批拒绝
    CONSTRAINT_VIOLATED,// 约束被违反

    // --- Beta 策略专属 ---
    GOAL_ANALYZED,      // 目标分析完成
    DECISION_MADE,      // LLM 做出决策
    GUARDRAIL_TRIGGERED,// 护栏被触发
    SELF_REFLECTION,    // 自我反思触发
    CONTEXT_COMPACTED,  // 上下文被压缩
    GOAL_REPRIORITIZED, // 目标优先级调整
    SPAWN_SUB_AGENT,    // 子 Agent 被派生
    REPLAN_REQUESTED,   // 请求重规划
    REPLAN_ASSESSED,    // 重规划可行性评估完成
    GOAL_DRIFT_DETECTED,// 目标漂移被检测
    CRITERIA_VERIFIED,  // 成功标准验证完成
    KNOWLEDGE_DEPOSITED, // 知识沉淀完成
    BETA_PLAN_GENERATED, // Beta plan 生成
    BETA_PLAN_REVISED    // Beta plan 修订
}
