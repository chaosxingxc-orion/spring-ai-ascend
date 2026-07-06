package com.huawei.ascend.edp.channel;

/**
 * ToolDataChannel 作用域定义。
 *
 * 定义数据在通道中的作用域范围。
 */
public enum ToolDataScope {
    /** 单次工具调用作用域，调用结束后自动清理。 */
    SINGLE_CALL,
    /** 单轮 ReAct 作用域，轮结束后自动清理。 */
    SINGLE_ROUND,
    /** 整个任务作用域，任务完成后清理。 */
    TASK,
    /** 持久作用域，需要手动清理。 */
    PERSISTENT
}
