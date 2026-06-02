package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.access.api.NotificationPort;
import com.huawei.ascend.service.queue.InternalEventQueue;

import java.util.Objects;

public final class DefaultNotificationPort implements NotificationPort {

    private final EgressQueueRegistry registry;

    public DefaultNotificationPort(EgressQueueRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void notify(NotificationFrame frame) {
        Objects.requireNonNull(frame, "frame");
        InternalEventQueue<NotificationFrame> queue = registry.findActive(frame.tenantId(), frame.sessionId())
                .orElseThrow(() -> new EgressDeliveryException(
                        "No active egress queue for tenantId=%s, sessionId=%s"
                                .formatted(frame.tenantId(), frame.sessionId())));
        queue.offer(frame);
    }
}
