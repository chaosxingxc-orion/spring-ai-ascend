package com.huawei.ascend.examples.workmate.memory.dto;

import com.huawei.ascend.examples.workmate.memory.MemorySettingsStore.MemorySettings;

public record MemorySettingsRequest(boolean enabled, boolean autoCapture) {
    public MemorySettings toSettings() {
        return new MemorySettings(enabled, autoCapture);
    }
}
