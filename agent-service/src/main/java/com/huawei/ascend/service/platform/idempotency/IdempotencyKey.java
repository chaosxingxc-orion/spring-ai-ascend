package com.huawei.ascend.service.platform.idempotency;

import java.util.UUID;

// scope: process-internal -- request-bound validated key; W1 IdempotencyRepository owns the persisted record.
public record IdempotencyKey(UUID value) {
    public static IdempotencyKey parse(String raw) {
        return new IdempotencyKey(UUID.fromString(raw.strip()));
    }
}
