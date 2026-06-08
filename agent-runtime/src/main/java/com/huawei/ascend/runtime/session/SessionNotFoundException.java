package com.huawei.ascend.runtime.session;


public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(SessionKey key) {
        super("Session not found: tenantId=%s, sessionId=%s".formatted(key.tenantId(), key.sessionId()));
    }
}
