package com.huawei.ascend.examples.workmate.session;

/** Raised when auto-archive cannot find enough unpinned, non-running sessions. */
public class InsufficientArchivableSessionsException extends RuntimeException {

    private final int requested;
    private final int available;
    private final int activeCount;
    private final int maxActive;

    public InsufficientArchivableSessionsException(
            int requested, int available, int activeCount, int maxActive) {
        super("Not enough archivable sessions (need " + requested + ", found " + available + ")");
        this.requested = requested;
        this.available = available;
        this.activeCount = activeCount;
        this.maxActive = maxActive;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public int getMaxActive() {
        return maxActive;
    }
}
