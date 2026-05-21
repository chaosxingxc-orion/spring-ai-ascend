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

    private int coreThreads = 4;
    private int maxThreads = 16;
    private int queueCapacity = 256;

    public RejectionPolicy getRejectionPolicy() {
        return rejectionPolicy;
    }

    public void setRejectionPolicy(RejectionPolicy rejectionPolicy) {
        this.rejectionPolicy = rejectionPolicy;
    }

    public int getCoreThreads() {
        return coreThreads;
    }

    public void setCoreThreads(int coreThreads) {
        this.coreThreads = coreThreads;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
