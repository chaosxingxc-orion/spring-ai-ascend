package com.huawei.ascend.examples.workmate.session;

import java.util.UUID;

public class SessionBusyException extends RuntimeException {

    private final UUID sessionId;

    public SessionBusyException(UUID sessionId) {
        super("Session is already running: " + sessionId);
        this.sessionId = sessionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
