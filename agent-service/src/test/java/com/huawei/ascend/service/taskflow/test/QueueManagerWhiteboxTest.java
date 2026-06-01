package com.huawei.ascend.service.taskflow.test;

import com.huawei.ascend.service.taskflow.queue.QueueFactory;
import com.huawei.ascend.service.taskflow.queue.QueueManager;
import com.huawei.ascend.service.taskflow.queue.QueueRegistration;
import com.huawei.ascend.service.taskflow.queue.TaskQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueManagerWhiteboxTest {

    @Test
    void factoryCanRegisterSessionQueueWithManager() {
        QueueManager manager = new QueueManager();

        TaskQueue<Object> queue = QueueFactory.inMemorySessionQueue("tenant", "session", manager);

        assertThat(manager.findBySession("tenant", "session")).containsSame(queue);
        assertThat(manager.registration(queue.queueId()))
                .map(QueueRegistration::sessionId)
                .contains("session");
        assertThat(manager.registrations()).hasSize(1);
    }

    @Test
    void unregisterRemovesQueueAndSessionLookup() {
        QueueManager manager = new QueueManager();
        TaskQueue<Object> queue = QueueFactory.inMemorySessionQueue("tenant", "session", manager);

        manager.unregister(queue.queueId());

        assertThat(manager.findByQueueId(queue.queueId())).isEmpty();
        assertThat(manager.findBySession("tenant", "session")).isEmpty();
        assertThat(manager.registrations()).isEmpty();
    }

    @Test
    void duplicateQueueIdIsRejected() {
        QueueManager manager = new QueueManager();
        QueueRegistration registration = QueueRegistration.session("tenant", "session");
        QueueFactory.inMemoryQueue(registration.queueId(), manager, registration);

        assertThatThrownBy(() -> QueueFactory.inMemoryQueue(registration.queueId(), manager, registration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("queue already registered");
    }
}
