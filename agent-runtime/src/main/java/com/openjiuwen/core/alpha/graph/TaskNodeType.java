package com.openjiuwen.core.alpha.graph;

/**
 * 任务节点类型。
 */
public enum TaskNodeType {

    /** 工具调用：调一个已注册的 @Tool 方法（确定性执行） */
    TOOL_CALL,

    /** LLM 调用：一次 LLM 推理（需要思考、判断） */
    LLM_CALL,

    /** 子 Agent：递归执行一个新的 Plan-Execute-Verify */
    SUB_AGENT
}
