package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.queue.InternalEventQueue;
import com.huawei.ascend.service.queue.QueueManager;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link EgressQueueRegistry} backed by the shared internal-event-queue
 * module. One {@link InternalEventQueue} of {@link NotificationFrame} is created per active session,
 * keyed by (tenant, session).
 */
public final class DefaultEgressQueueRegistry implements EgressQueueRegistry {

    private final QueueManager queueManager;
    private final ConcurrentHashMap<Key, EgressBinding> bindings = new ConcurrentHashMap<>();

    public DefaultEgressQueueRegistry(QueueManager queueManager) {
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager");
    }

    @Override
    public InternalEventQueue<NotificationFrame> getOrCreate(EgressBinding binding) {
        Objects.requireNonNull(binding, "binding");
        Key key = Key.from(binding.tenantId(), binding.sessionId());
        bindings.put(key, binding);
        return queueManager.getOrCreate(queueIdValue(binding), NotificationFrame.class);
    }

    @Override
    public Optional<InternalEventQueue<NotificationFrame>> find(String tenantId, String sessionId) {
        return findBinding(tenantId, sessionId)
                .flatMap(binding -> queueManager.find(queueIdValue(binding)))
                .map(DefaultEgressQueueRegistry::typed);
    }

    @Override
    public Optional<EgressBinding> findBinding(String tenantId, String sessionId) {
        return Optional.ofNullable(bindings.get(Key.from(tenantId, sessionId)));
    }

    @Override
    public void remove(String tenantId, String sessionId) {
        Key key = Key.from(tenantId, sessionId);
        EgressBinding binding = bindings.remove(key);
        if (binding != null) {
            queueManager.close(queueIdValue(binding));
        }
    }

    @SuppressWarnings("unchecked")
    private static InternalEventQueue<NotificationFrame> typed(InternalEventQueue<?> queue) {
        return (InternalEventQueue<NotificationFrame>) queue;
    }

    private static String queueIdValue(EgressBinding binding) {
        return binding.tenantId() + ":" + binding.sessionId() + ":egress";
    }

    private record Key(String tenantId, String sessionId) {
        private Key {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(sessionId, "sessionId");
        }

        static Key from(String tenantId, String sessionId) {
            return new Key(tenantId, sessionId);
        }
    }
}
