package com.huawei.ascend.examples.workmate.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.member-runtimes")
public class WorkmateMemberRuntimeProperties {

    /** When true, members with configured base URLs use outbound A2A instead of in-process AgentRunExecutor. */
    private boolean enabled = false;

    private int requestTimeoutSeconds = 300;

    private int healthProbeTimeoutSeconds = 5;

    /** expertId → base URL (e.g. http://member-prd-writer:8081). */
    private Map<String, String> members = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public int getHealthProbeTimeoutSeconds() {
        return healthProbeTimeoutSeconds;
    }

    public void setHealthProbeTimeoutSeconds(int healthProbeTimeoutSeconds) {
        this.healthProbeTimeoutSeconds = healthProbeTimeoutSeconds;
    }

    public Map<String, String> getMembers() {
        return members;
    }

    public void setMembers(Map<String, String> members) {
        this.members = members != null ? members : new HashMap<>();
    }
}
