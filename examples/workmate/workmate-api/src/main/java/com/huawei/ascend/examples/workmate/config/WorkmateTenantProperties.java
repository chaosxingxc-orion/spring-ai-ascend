package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.tenant")
public record WorkmateTenantProperties(String id, TenantQuotaProperties quota) {

    public WorkmateTenantProperties {
        if (id == null || id.isBlank()) {
            id = "default";
        }
        if (quota == null) {
            quota = new TenantQuotaProperties(0, 0L, 0L, 80);
        }
    }

    public record TenantQuotaProperties(
            int maxActiveSessions, long maxMonthlyTokens, long maxStorageBytes, int warnThresholdPercent) {

        public TenantQuotaProperties {
            if (warnThresholdPercent <= 0 || warnThresholdPercent > 100) {
                warnThresholdPercent = 80;
            }
        }
    }
}
