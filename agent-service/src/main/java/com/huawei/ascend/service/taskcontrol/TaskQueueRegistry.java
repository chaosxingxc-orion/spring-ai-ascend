package com.huawei.ascend.service.taskcontrol;

import com.huawei.ascend.service.queue.InternalEventQueue;
import com.huawei.ascend.service.queue.QueueManager;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class TaskQueueRegistry {

    private final QueueManager queueManager;
    private final ConcurrentMap<SessionKey, SessionTasks> sessions = new ConcurrentHashMap<>();

    TaskQueueRegistry(QueueManager queueManager) {
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager");
    }

    void add(Task task) {
        Objects.requireNonNull(task, "task");
        SessionTasks session = session(task.getTenantId(), task.getSessionId());
        session.tasksById().put(task.getTaskId(), task);
        session.queue().offer(task);
    }

    Optional<Task> findTask(String tenantId, String sessionId, String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(session(tenantId, sessionId).tasksById().get(taskId));
    }

    List<Task> tasks(String tenantId, String sessionId) {
        return session(tenantId, sessionId).tasksById().values().stream()
                .sorted(Comparator.comparing(Task::getCreatedAt).thenComparing(Task::getTaskId))
                .toList();
    }

    private SessionTasks session(String tenantId, String sessionId) {
        SessionKey key = new SessionKey(tenantId, sessionId);
        return sessions.computeIfAbsent(key, ignored -> new SessionTasks(
                queueManager.getOrCreate(queueId(key), Task.class),
                new ConcurrentHashMap<>()));
    }

    private static String queueId(SessionKey key) {
        return "task:" + key.tenantId() + ":" + key.sessionId();
    }

    private record SessionTasks(InternalEventQueue<Task> queue, ConcurrentMap<String, Task> tasksById) {
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
