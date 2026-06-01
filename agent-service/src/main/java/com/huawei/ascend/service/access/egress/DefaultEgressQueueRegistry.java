package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.queue.QueueFactory;
import com.huawei.ascend.service.queue.TaskQueue;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link EgressQueueRegistry} backed by the shared internal-event-queue
 * module. One {@link TaskQueue} of {@link NotificationFrame} is created per task,
 * keyed by (tenant, session, task).
 */
public final class DefaultEgressQueueRegistry implements EgressQueueRegistry {

    private final ConcurrentHashMap<Key, TaskQueue<NotificationFrame>> queues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, EgressBinding> bindings = new ConcurrentHashMap<>();

    @Override
    public TaskQueue<NotificationFrame> getOrCreate(EgressBinding binding) {
        Objects.requireNonNull(binding, "binding");
        Key key = Key.from(binding.tenantId(), binding.sessionId(), binding.taskId());
        bindings.putIfAbsent(key, binding);
        return queues.computeIfAbsent(key,
                ignored -> QueueFactory.inMemoryQueue(queueIdValue(binding)));
    }

    @Override
    public Optional<TaskQueue<NotificationFrame>> find(String tenantId, String sessionId, String taskId) {
        return Optional.ofNullable(queues.get(Key.from(tenantId, sessionId, taskId)));
    }

    @Override
    public Optional<EgressBinding> findBinding(String tenantId, String sessionId, String taskId) {
        return Optional.ofNullable(bindings.get(Key.from(tenantId, sessionId, taskId)));
    }

    @Override
    public void remove(String tenantId, String sessionId, String taskId) {
        Key key = Key.from(tenantId, sessionId, taskId);
        queues.remove(key);
        bindings.remove(key);
    }

    private static String queueIdValue(EgressBinding binding) {
        return binding.tenantId() + ":" + binding.sessionId() + ":" + binding.taskId() + ":egress";
    }

    private record Key(String tenantId, String sessionId, String taskId) {
        private Key {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(taskId, "taskId");
        }

        static Key from(String tenantId, String sessionId, String taskId) {
            return new Key(tenantId, sessionId, taskId);
        }
    }
}
