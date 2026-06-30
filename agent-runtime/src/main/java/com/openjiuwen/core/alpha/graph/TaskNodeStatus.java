package com.openjiuwen.core.alpha.graph;

/**
 * 任务节点执行状态。
 */
public enum TaskNodeStatus {

    /** 待执行 */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 执行完成 */
    COMPLETED,

    /** 执行失败 */
    FAILED,

    /** 被跳过（因为上游失败） */
    SKIPPED
}
