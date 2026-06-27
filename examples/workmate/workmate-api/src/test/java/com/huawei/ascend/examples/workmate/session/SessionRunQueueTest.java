package com.huawei.ascend.examples.workmate.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionRunQueueTest {

    @Test
    void enqueuesAndPollsInFifoOrder() {
        SessionRunQueue queue = new SessionRunQueue();
        UUID sessionId = UUID.randomUUID();

        assertThat(queue.enqueue(sessionId, "first", null, null)).isEqualTo(1);
        assertThat(queue.enqueue(sessionId, "second", null, null)).isEqualTo(2);
        assertThat(queue.depth(sessionId)).isEqualTo(2);

        assertThat(queue.poll(sessionId)).get().extracting(SessionRunQueue.QueuedPrompt::message).isEqualTo("first");
        assertThat(queue.poll(sessionId)).get().extracting(SessionRunQueue.QueuedPrompt::message).isEqualTo("second");
        assertThat(queue.depth(sessionId)).isZero();
    }

    @Test
    void rejectsWhenQueueIsFull() {
        SessionRunQueue queue = new SessionRunQueue();
        UUID sessionId = UUID.randomUUID();

        for (int index = 0; index < SessionRunQueue.MAX_QUEUE_SIZE; index++) {
            queue.enqueue(sessionId, "msg-" + index, null, null);
        }

        assertThatThrownBy(() -> queue.enqueue(sessionId, "overflow", null, null))
                .isInstanceOf(SessionQueueFullException.class);
    }

    @Test
    void clearsQueuedPrompts() {
        SessionRunQueue queue = new SessionRunQueue();
        UUID sessionId = UUID.randomUUID();

        queue.enqueue(sessionId, "first", null, null);
        queue.enqueue(sessionId, "second", null, null);
        assertThat(queue.clear(sessionId)).isEqualTo(2);
        assertThat(queue.depth(sessionId)).isZero();
        assertThat(queue.clear(sessionId)).isZero();
    }
}
