package com.huawei.ascend.service.platform.web.runs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for run-dispatch executor overload behavior.
 */
@ConfigurationProperties(prefix = "app.runs.dispatch")
public class RunDispatchProperties {

    public enum RejectionPolicy {
        CALLER_RUNS,
        ABORT
    }

    /**
     * Overload rejection policy for the dedicated run-dispatch executor.
     * Default keeps current behavior for backward compatibility.
     */
    private RejectionPolicy rejectionPolicy = RejectionPolicy.CALLER_RUNS;

    public RejectionPolicy getRejectionPolicy() {
        return rejectionPolicy;
    }

    public void setRejectionPolicy(RejectionPolicy rejectionPolicy) {
        this.rejectionPolicy = rejectionPolicy;
    }
}
