package com.openjiuwen.core.kernel.model;

import java.time.Instant;

/**
 * 检查点——Agent 执行状态的时间切片。
 * 在关键节点（规划完成、每层执行完成、验证完成）自动保存。
 * 崩溃恢复时从最近的 Checkpoint 继续执行。
 *
 * @param checkpointId 检查点唯一 ID
 * @param taskId       所属任务 ID
 * @param phase        当前阶段：PLANNING / EXECUTING / VERIFYING
 * @param stepIndex    步骤序号（规划完成=0，Layer 0 完成后=1，依此类推）
 * @param stateJson    序列化的执行状态（对话历史、中间结果、已完成的节点 ID 等）
 * @param timestamp    保存时间
 */
public record Checkpoint(
    CheckpointId checkpointId,
    TaskId taskId,
    String phase,
    int stepIndex,
    String stateJson,
    Instant timestamp
) {

    /** 创建新的检查点 */
    public static Checkpoint of(TaskId taskId, String phase, int stepIndex, String stateJson) {
        return new Checkpoint(CheckpointId.generate(), taskId, phase, stepIndex, stateJson, Instant.now());
    }
}
