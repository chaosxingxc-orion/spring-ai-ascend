package com.huawei.ascend.service.queue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueManagerTest {

    @Test
    void getOrCreateReturnsSameQueueForSameIdAndType() {
        QueueManager manager = new QueueManager();

        InternalEventQueue<String> first = manager.getOrCreate("access.data", String.class);
        InternalEventQueue<String> second = manager.getOrCreate("access.data", String.class);

        assertThat(second).isSameAs(first);
        assertThat(manager.find("access.data")).containsSame(first);
    }

    @Test
    void getOrCreateRejectsSameIdWithDifferentPayloadType() {
        QueueManager manager = new QueueManager();
        manager.getOrCreate("shared.id", String.class);

        assertThatThrownBy(() -> manager.getOrCreate("shared.id", Integer.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payload type mismatch");
    }

    @Test
    void closeRemovesQueueAndClosesStream() {
        QueueManager manager = new QueueManager();
        InternalEventQueue<String> queue = manager.getOrCreate("access.data", String.class);

        manager.close("access.data");

        assertThat(manager.find("access.data")).isEmpty();
        assertThatThrownBy(() -> queue.offer("late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }
}
