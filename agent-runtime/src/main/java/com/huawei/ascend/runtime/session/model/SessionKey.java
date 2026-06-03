package com.huawei.ascend.service.session.model;

import java.util.Objects;

public record SessionKey(String tenantId, String sessionId) {
    public SessionKey {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }
}
