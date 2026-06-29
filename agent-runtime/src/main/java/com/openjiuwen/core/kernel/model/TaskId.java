package com.openjiuwen.core.kernel.model;

/**
 * 任务唯一标识。
 * 全局唯一，用于检查点恢复、事件溯源、日志追踪。
 *
 * @param value UUID 字符串
 */
public record TaskId(String value) {

    public TaskId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskId 不能为空");
        }
    }

    /** 生成新的随机 TaskId */
    public static TaskId generate() {
        return new TaskId(java.util.UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
