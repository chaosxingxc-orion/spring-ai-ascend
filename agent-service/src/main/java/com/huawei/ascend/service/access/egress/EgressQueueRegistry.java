package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.queue.InternalEventQueue;

import java.util.Optional;

/**
 * Per-session egress queue registry.
 *
 * <p>The access layer keeps one outbound queue per active reply channel
 * so it can turn the internal notification stream back into the caller. The
 * queues are backed by the shared internal-event-queue
 * ({@link com.huawei.ascend.service.queue}) implementation rather than a private
 * queue type, so the service exposes a single queue abstraction.
 */
public interface EgressQueueRegistry {

    InternalEventQueue<NotificationFrame> getOrCreate(EgressBinding binding);

    Optional<InternalEventQueue<NotificationFrame>> find(String tenantId, String sessionId, String replyId);

    Optional<InternalEventQueue<NotificationFrame>> findActive(String tenantId, String sessionId);

    Optional<EgressBinding> findBinding(String tenantId, String sessionId, String replyId);

    Optional<EgressBinding> findActiveBinding(String tenantId, String sessionId);

    void remove(String tenantId, String sessionId, String replyId);
}
