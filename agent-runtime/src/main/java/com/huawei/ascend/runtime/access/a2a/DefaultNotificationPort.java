package com.huawei.ascend.runtime.access.a2a;

import com.huawei.ascend.runtime.access.api.NotificationPort;
import com.huawei.ascend.runtime.access.AgentNotification;
import com.huawei.ascend.runtime.common.Timing;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultNotificationPort implements NotificationPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNotificationPort.class);

    private final A2aOutputMapper outputMapper;
    private final A2aOutputRegistry outputRegistry;

    public DefaultNotificationPort(A2aOutputMapper outputMapper, A2aOutputRegistry outputRegistry) {
        this.outputMapper = Objects.requireNonNull(outputMapper, "outputMapper");
        this.outputRegistry = Objects.requireNonNull(outputRegistry, "outputRegistry");
    }

    @Override
    public void notify(AgentNotification notification) {
        Objects.requireNonNull(notification, "notification");
        long startedNanos = System.nanoTime();
        A2aOutputHandle handle = new A2aOutputHandle(
                notification.tenantId(), notification.sessionId(), notification.taskId());
        outputRegistry.append(handle, outputMapper.toA2aOutput(notification), outputMapper.toResponseEvent(notification));
        LOGGER.info("trace stage=a2a-egress-deliver tenantId={} sessionId={} taskId={} type={} status={} terminal={} durationMs={}",
                notification.tenantId(),
                notification.sessionId(),
                notification.taskId(),
                notification.type(),
                notification.status(),
                notification.terminal(),
                Timing.elapsedMs(startedNanos));
    }
}
