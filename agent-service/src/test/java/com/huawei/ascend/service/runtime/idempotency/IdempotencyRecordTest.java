package com.huawei.ascend.service.runtime.idempotency;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class IdempotencyRecordTest {

    @Test
    void idempotencyRecord_requires_tenantId() {
        assertThatNullPointerException()
            .isThrownBy(() -> new IdempotencyRecord("key-1", null, UUID.randomUUID(), Instant.now()))
            .withMessageContaining("tenantId");
    }

    @Test
    void idempotencyRecord_requires_idempotencyKey() {
        assertThatNullPointerException()
            .isThrownBy(() -> new IdempotencyRecord(null, "tenant-1", UUID.randomUUID(), Instant.now()))
            .withMessageContaining("idempotencyKey");
    }

    @Test
    void idempotencyRecord_valid_construction_carries_mandatory_fields() {
        UUID runId = UUID.randomUUID();
        var record = new IdempotencyRecord("key-1", "tenant-1", runId, Instant.now());
        assertThat(record.tenantId()).isEqualTo("tenant-1");
        assertThat(record.idempotencyKey()).isEqualTo("key-1");
        assertThat(record.runId()).isEqualTo(runId);
    }
}
