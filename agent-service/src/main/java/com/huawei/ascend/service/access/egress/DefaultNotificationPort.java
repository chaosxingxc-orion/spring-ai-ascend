package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.access.api.NotificationPort;
import com.huawei.ascend.service.access.temp.L3QueuePlaceholders.Queue;

import java.util.Objects;

public final class DefaultNotificationPort implements NotificationPort {

    private final EgressQueueRegistry registry;

    public DefaultNotificationPort(EgressQueueRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void notify(NotificationFrame frame) {
        Objects.requireNonNull(frame, "frame");
        Queue queue = registry.find(frame.tenantId(), frame.sessionId(), frame.taskId())
                .orElseThrow(() -> new EgressDeliveryException(
                        "No egress queue for tenantId=%s, sessionId=%s, taskId=%s"
                                .formatted(frame.tenantId(), frame.sessionId(), frame.taskId())));
        queue.push(frame);
    }
}


