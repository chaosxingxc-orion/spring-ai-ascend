package com.huawei.ascend.examples.workmate.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionRunLockTest {

    @Test
    void acquireAndReleaseRunLock() {
        SessionRunLock lock = new SessionRunLock();
        UUID sessionId = UUID.randomUUID();

        assertThat(lock.tryAcquire(sessionId, "run-a")).isTrue();
        assertThat(lock.isLocked(sessionId)).isTrue();
        assertThat(lock.tryAcquire(sessionId, "run-b")).isFalse();

        lock.release(sessionId, "run-b");
        assertThat(lock.isLocked(sessionId)).isTrue();

        lock.release(sessionId, "run-a");
        assertThat(lock.isLocked(sessionId)).isFalse();
        assertThat(lock.tryAcquire(sessionId, "run-c")).isTrue();
    }
}
