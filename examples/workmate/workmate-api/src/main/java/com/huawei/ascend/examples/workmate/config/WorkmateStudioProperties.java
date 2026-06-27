package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.studio")
public class WorkmateStudioProperties {

    private boolean enabled = true;
    private boolean auditEnabled = true;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean auditEnabled() {
        return auditEnabled;
    }

    public void setAuditEnabled(boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
    }
}
