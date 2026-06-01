package com.huawei.ascend.service.queue;

/**
 * Static Internal Event Queue (IEQ) factory for the current in-memory backend.
 *
 * <p>This is intentionally not an SPI. Provider extensibility belongs in later
 * backend implementations; this wave only freezes the construction surface used
 * by session/access code.
 */
public final class QueueFactory {

    private QueueFactory() {
    }

    public static <T> TaskQueue<T> inMemoryQueue(String queueId) {
        return new InMemoryTaskQueue<>(queueId);
    }

    public static <T> TaskQueue<T> inMemoryQueue(
            String queueId,
            QueueManager manager,
            QueueRegistration registration) {
        TaskQueue<T> queue = inMemoryQueue(queueId);
        return manager.register(queue, registration);
    }

    public static <T> TaskQueue<T> inMemorySessionQueue(
            String tenantId,
            String sessionId,
            QueueManager manager) {
        QueueRegistration registration = QueueRegistration.session(tenantId, sessionId);
        return inMemoryQueue(registration.queueId(), manager, registration);
    }
}
