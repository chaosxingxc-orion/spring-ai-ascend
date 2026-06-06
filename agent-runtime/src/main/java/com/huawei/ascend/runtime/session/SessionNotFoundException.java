package com.huawei.ascend.runtime.session;

import com.huawei.ascend.runtime.session.model.SessionKey;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(SessionKey key) {
        super("Session not found: tenantId=%s, sessionId=%s".formatted(key.tenantId(), key.sessionId()));
    }
}
