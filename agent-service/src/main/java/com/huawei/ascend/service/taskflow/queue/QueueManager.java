package com.huawei.ascend.service.taskflow.queue;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class QueueManager {

    private final ConcurrentMap<String, TaskQueue<?>> queuesById = new ConcurrentHashMap<>();
    private final ConcurrentMap<SessionKey, String> queueIdsBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, QueueRegistration> registrations = new ConcurrentHashMap<>();

    public <T> TaskQueue<T> register(TaskQueue<T> queue, QueueRegistration registration) {
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(registration, "registration");
        if (!queue.queueId().equals(registration.queueId())) {
            throw new IllegalArgumentException("queueId mismatch");
        }
        TaskQueue<?> existing = queuesById.putIfAbsent(queue.queueId(), queue);
        if (existing != null && existing != queue) {
            throw new IllegalStateException("queue already registered: " + queue.queueId());
        }
        SessionKey sessionKey = new SessionKey(registration.tenantId(), registration.sessionId());
        String existingQueueId = queueIdsBySession.putIfAbsent(sessionKey, queue.queueId());
        if (existingQueueId != null && !existingQueueId.equals(queue.queueId())) {
            throw new IllegalStateException("session already has queue: " + registration.sessionId());
        }
        registrations.putIfAbsent(queue.queueId(), registration);
        return queue;
    }

    public Optional<TaskQueue<?>> findByQueueId(String queueId) {
        Objects.requireNonNull(queueId, "queueId");
        return Optional.ofNullable(queuesById.get(queueId));
    }

    public Optional<TaskQueue<?>> findBySession(String tenantId, String sessionId) {
        String queueId = queueIdsBySession.get(new SessionKey(tenantId, sessionId));
        return queueId == null ? Optional.empty() : findByQueueId(queueId);
    }

    public Optional<QueueRegistration> registration(String queueId) {
        Objects.requireNonNull(queueId, "queueId");
        return Optional.ofNullable(registrations.get(queueId));
    }

    public List<QueueRegistration> registrations() {
        return registrations.values().stream()
                .sorted(Comparator.comparing(QueueRegistration::createdAt)
                        .thenComparing(QueueRegistration::queueId))
                .toList();
    }

    public void unregister(String queueId) {
        Objects.requireNonNull(queueId, "queueId");
        QueueRegistration registration = registrations.remove(queueId);
        queuesById.remove(queueId);
        if (registration != null) {
            queueIdsBySession.remove(new SessionKey(registration.tenantId(), registration.sessionId()), queueId);
        }
    }

    private record SessionKey(String tenantId, String sessionId) {
        private SessionKey {
            tenantId = requireNonBlank(tenantId, "tenantId");
            sessionId = requireNonBlank(sessionId, "sessionId");
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
