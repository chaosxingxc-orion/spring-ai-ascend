package com.huawei.ascend.service.sessionmanage.store.memory;

import com.huawei.ascend.service.sessionmanage.model.SessionKey;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(SessionKey key) {
        super("Session not found: tenantId=%s, sessionId=%s".formatted(key.tenantId(), key.sessionId()));
    }
}
