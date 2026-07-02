package com.openjiuwen.core.kernel.model;

/**
 * 检查点 ID。
 */
public record CheckpointId(String value) {

    public CheckpointId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CheckpointId 不能为空");
        }
    }

    public static CheckpointId generate() {
        return new CheckpointId(java.util.UUID.randomUUID().toString());
    }
}
