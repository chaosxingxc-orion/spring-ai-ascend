package com.huawei.ascend.service.session.store.memory;

import com.huawei.ascend.service.session.model.SessionKey;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(SessionKey key) {
        super("Session not found: tenantId=%s, sessionId=%s".formatted(key.tenantId(), key.sessionId()));
    }
}
