package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.queue.TaskQueue;

import java.util.Optional;

/**
 * Per-request egress queue registry.
 *
 * <p>The access layer keeps one outbound queue per in-flight task so it can turn
 * the internal notification stream back into the caller's reply channel. The
 * queues are backed by the shared internal-event-queue
 * ({@link com.huawei.ascend.service.queue}) implementation rather than a private
 * queue type, so the service exposes a single queue abstraction.
 */
public interface EgressQueueRegistry {

    TaskQueue<NotificationFrame> getOrCreate(EgressBinding binding);

    Optional<TaskQueue<NotificationFrame>> find(String tenantId, String sessionId, String taskId);

    Optional<EgressBinding> findBinding(String tenantId, String sessionId, String taskId);

    void remove(String tenantId, String sessionId, String taskId);
}
