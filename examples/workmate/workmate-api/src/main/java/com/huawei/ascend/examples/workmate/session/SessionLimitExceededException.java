package com.huawei.ascend.examples.workmate.session;

public class SessionLimitExceededException extends RuntimeException {

    private final int activeCount;
    private final int maxActive;

    public SessionLimitExceededException(int activeCount, int maxActive) {
        super("Active session limit reached (" + activeCount + "/" + maxActive + ")");
        this.activeCount = activeCount;
        this.maxActive = maxActive;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public int getMaxActive() {
        return maxActive;
    }
}
