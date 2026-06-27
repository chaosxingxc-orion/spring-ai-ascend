package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.session")
public record WorkmateSessionProperties(int maxActive, boolean autoArchiveOnCreate, boolean protectRunning) {

    public WorkmateSessionProperties {
        if (maxActive <= 0) {
            maxActive = 50;
        }
    }
}
