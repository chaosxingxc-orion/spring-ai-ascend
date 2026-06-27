package com.huawei.ascend.examples.workmate.share.dto;

import java.util.UUID;

/** W49-E3 — time-limited file download link. */
public record FileShareRequest(UUID sessionId, String path, Integer expiresInHours) {

    public int normalizedExpiresInHours() {
        if (expiresInHours == null || expiresInHours <= 0) {
            return 72;
        }
        return Math.min(expiresInHours, 24 * 30);
    }
}
