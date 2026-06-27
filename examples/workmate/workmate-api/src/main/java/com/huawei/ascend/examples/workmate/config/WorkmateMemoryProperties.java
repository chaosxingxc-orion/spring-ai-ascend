package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.memory")
public record WorkmateMemoryProperties(String ownerId, int maxInjectChars, boolean autoCapture) {

    public WorkmateMemoryProperties {
        if (ownerId == null || ownerId.isBlank()) {
            ownerId = "default";
        }
        if (maxInjectChars <= 0) {
            maxInjectChars = 2000;
        }
    }
}
