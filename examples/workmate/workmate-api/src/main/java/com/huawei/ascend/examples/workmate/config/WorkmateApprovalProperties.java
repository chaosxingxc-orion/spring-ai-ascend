package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.approval")
public record WorkmateApprovalProperties(long timeoutSeconds) {

    public WorkmateApprovalProperties {
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 120;
        }
    }
}
