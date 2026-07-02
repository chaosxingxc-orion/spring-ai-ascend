package com.openjiuwen.core.kernel.model;

import java.time.Duration;
import java.time.Instant;

/**
 * 任务执行结果。
 * AgentKernel 完成 execute() 后返回此对象。
 *
 * @param taskId    任务 ID
 * @param status    任务最终状态
 * @param output    Agent 输出的文本（给用户看的最终回答）
 * @param rawResult 原始结果对象（可能是 Map、POJO 等，用于程序化消费）
 * @param duration  执行耗时
 * @param completedAt 完成时间
 */
public record TaskResult(
    TaskId taskId,
    TaskStatus status,
    String output,
    Object rawResult,
    Duration duration,
    Instant completedAt
) {

    /** 创建成功的 TaskResult */
    public static TaskResult success(TaskId taskId, String output, Duration duration) {
        return new TaskResult(taskId, new TaskStatus.Completed(), output, null, duration, Instant.now());
    }

    /** 创建失败的 TaskResult */
    public static TaskResult failure(TaskId taskId, String errorMessage, Duration duration) {
        return new TaskResult(taskId, new TaskStatus.Failed(errorMessage), errorMessage, null, duration, Instant.now());
    }
}
