package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.temp.L3QueuePlaceholders.Queue;
import com.huawei.ascend.service.access.temp.L3QueuePlaceholders.QueueFactory;
import com.huawei.ascend.service.access.temp.L3QueuePlaceholders.QueueId;
import com.huawei.ascend.service.access.temp.L3QueuePlaceholders.QueueSpec;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultEgressQueueRegistry implements EgressQueueRegistry {

    private final QueueFactory queueFactory;
    private final ConcurrentHashMap<Key, Queue> queues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, EgressBinding> bindings = new ConcurrentHashMap<>();

    public DefaultEgressQueueRegistry(QueueFactory queueFactory) {
        this.queueFactory = Objects.requireNonNull(queueFactory, "queueFactory");
    }

    @Override
    public Queue getOrCreate(EgressBinding binding) {
        Objects.requireNonNull(binding, "binding");
        Key key = Key.from(binding.tenantId(), binding.sessionId(), binding.taskId());
        bindings.putIfAbsent(key, binding);
        return queues.computeIfAbsent(key, ignored -> queueFactory.createQueue(new QueueSpec(
                new QueueId(queueIdValue(binding)),
                binding.tenantId(),
                binding.sessionId(),
                binding.taskId())));
    }

    @Override
    public Optional<Queue> find(String tenantId, String sessionId, String taskId) {
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


