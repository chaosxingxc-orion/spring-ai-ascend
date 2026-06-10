package com.huawei.ascend.runtime.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryIdempotencyStoreTest {

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

    @Test
    void firstClaimAcquiresSecondIsInFlightCompletedReplays() {
        assertThat(store.claim("tenant-1", "msg-1")).isEqualTo(IdempotencyStore.Claim.ACQUIRED);
        assertThat(store.claim("tenant-1", "msg-1")).isEqualTo(IdempotencyStore.Claim.IN_FLIGHT);

        store.complete("tenant-1", "msg-1", "task-9");

        assertThat(store.claim("tenant-1", "msg-1")).isEqualTo(IdempotencyStore.Claim.REPLAY);
        assertThat(store.completedReference("tenant-1", "msg-1")).contains("task-9");
    }

    @Test
    void keysAreTenantScoped() {
        store.claim("tenant-1", "msg-1");

        assertThat(store.claim("tenant-2", "msg-1")).isEqualTo(IdempotencyStore.Claim.ACQUIRED);
    }

    @Test
    void releaseMakesTheKeyClaimableAgain() {
        store.claim("tenant-1", "msg-1");
        store.release("tenant-1", "msg-1");

        assertThat(store.claim("tenant-1", "msg-1")).isEqualTo(IdempotencyStore.Claim.ACQUIRED);
    }
}
