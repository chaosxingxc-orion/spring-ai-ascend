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
 * module. One {@link InternalEventQueue} of {@link NotificationFrame} is created per active reply,
 * keyed by (tenant, session, reply).
 */
public final class DefaultEgressQueueRegistry implements EgressQueueRegistry {

    private final QueueManager queueManager;
    private final ConcurrentHashMap<Key, EgressBinding> bindings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SessionKey, String> activeReplies = new ConcurrentHashMap<>();

    public DefaultEgressQueueRegistry(QueueManager queueManager) {
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager");
    }

    @Override
    public InternalEventQueue<NotificationFrame> getOrCreate(EgressBinding binding) {
        Objects.requireNonNull(binding, "binding");
        Key key = Key.from(binding.tenantId(), binding.sessionId(), binding.replyId());
        bindings.putIfAbsent(key, binding);
        activeReplies.put(new SessionKey(binding.tenantId(), binding.sessionId()), binding.replyId());
        return queueManager.getOrCreate(queueIdValue(binding), NotificationFrame.class);
    }

    @Override
    public Optional<InternalEventQueue<NotificationFrame>> find(String tenantId, String sessionId, String replyId) {
        return findBinding(tenantId, sessionId, replyId)
                .flatMap(binding -> queueManager.find(queueIdValue(binding)))
                .map(DefaultEgressQueueRegistry::typed);
    }

    @Override
    public Optional<InternalEventQueue<NotificationFrame>> findActive(String tenantId, String sessionId) {
        return activeReplyId(tenantId, sessionId).flatMap(replyId -> find(tenantId, sessionId, replyId));
    }

    @Override
    public Optional<EgressBinding> findBinding(String tenantId, String sessionId, String replyId) {
        return Optional.ofNullable(bindings.get(Key.from(tenantId, sessionId, replyId)));
    }

    @Override
    public Optional<EgressBinding> findActiveBinding(String tenantId, String sessionId) {
        return activeReplyId(tenantId, sessionId).flatMap(replyId -> findBinding(tenantId, sessionId, replyId));
    }

    @Override
    public void remove(String tenantId, String sessionId, String replyId) {
        Key key = Key.from(tenantId, sessionId, replyId);
        EgressBinding binding = bindings.remove(key);
        if (binding != null) {
            queueManager.close(queueIdValue(binding));
        }
        activeReplies.remove(new SessionKey(tenantId, sessionId), replyId);
    }

    @SuppressWarnings("unchecked")
    private static InternalEventQueue<NotificationFrame> typed(InternalEventQueue<?> queue) {
        return (InternalEventQueue<NotificationFrame>) queue;
    }

    private static String queueIdValue(EgressBinding binding) {
        return binding.tenantId() + ":" + binding.sessionId() + ":" + binding.replyId() + ":egress";
    }

    private Optional<String> activeReplyId(String tenantId, String sessionId) {
        return Optional.ofNullable(activeReplies.get(new SessionKey(tenantId, sessionId)));
    }

    private record Key(String tenantId, String sessionId, String replyId) {
        private Key {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(replyId, "replyId");
        }

        static Key from(String tenantId, String sessionId, String replyId) {
            return new Key(tenantId, sessionId, replyId);
        }
    }

    private record SessionKey(String tenantId, String sessionId) {
        private SessionKey {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(sessionId, "sessionId");
        }
    }
}
