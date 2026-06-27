package com.huawei.ascend.examples.workmate.session;

import java.util.UUID;

public class SessionQueueFullException extends RuntimeException {

    private final UUID sessionId;
    private final int maxSize;

    public SessionQueueFullException(UUID sessionId, int maxSize) {
        super("Session prompt queue is full (max " + maxSize + "): " + sessionId);
        this.sessionId = sessionId;
        this.maxSize = maxSize;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getMaxSize() {
        return maxSize;
    }
}
