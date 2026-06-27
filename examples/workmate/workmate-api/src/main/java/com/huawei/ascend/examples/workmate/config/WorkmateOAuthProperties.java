package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.oauth")
public class WorkmateOAuthProperties {

    /** Serves {@code /oauth/mock-authorize} for local dogfood redirect walkthrough. */
    private boolean mockEnabled = true;

    public boolean mockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }
}
