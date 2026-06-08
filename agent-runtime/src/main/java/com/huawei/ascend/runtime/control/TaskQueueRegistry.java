package com.huawei.ascend.runtime.control;

import com.huawei.ascend.runtime.common.Guards;
import com.huawei.ascend.runtime.queue.InternalEventQueue;
import com.huawei.ascend.runtime.queue.QueueManager;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TaskQueueRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskQueueRegistry.class);

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
        LOGGER.info("task queue add tenantId={} sessionId={} taskId={} agentId={} state={} sessionTaskCount={}",
                task.getTenantId(),
                task.getSessionId(),
                task.getTaskId(),
                task.getAgentId(),
                task.getState(),
                session.tasksById().size());
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
        return sessions.computeIfAbsent(key, ignored -> {
            String queueId = queueId(key);
            LOGGER.info("task queue session create tenantId={} sessionId={} queueId={}",
                    key.tenantId(), key.sessionId(), queueId);
            return new SessionTasks(
                    queueManager.getOrCreate(queueId, Task.class),
                    new ConcurrentHashMap<>());
        });
    }

    private static String queueId(SessionKey key) {
        return "task:" + key.tenantId() + ":" + key.sessionId();
    }

    private record SessionTasks(InternalEventQueue<Task> queue, ConcurrentMap<String, Task> tasksById) {
    }

    private record SessionKey(String tenantId, String sessionId) {
        private SessionKey {
            tenantId = Guards.requireNonBlank(tenantId, "tenantId");
            sessionId = Guards.requireNonBlank(sessionId, "sessionId");
        }
    }
}
