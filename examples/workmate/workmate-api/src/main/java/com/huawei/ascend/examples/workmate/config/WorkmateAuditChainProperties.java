package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.audit-chain")
public record WorkmateAuditChainProperties(int projectorBatchSize, long projectorFixedDelayMs) {
    public WorkmateAuditChainProperties {
        if (projectorBatchSize <= 0) {
            projectorBatchSize = 100;
        }
        if (projectorFixedDelayMs <= 0) {
            projectorFixedDelayMs = 5000L;
        }
    }
}
