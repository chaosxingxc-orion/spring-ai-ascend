package com.huawei.ascend.service.access.temp;

import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.model.AccessIntent;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.access.model.NotificationType;
import com.huawei.ascend.service.access.api.NotificationPort;
import com.huawei.ascend.service.access.core.TaskHandler;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Temporary L4 placeholder used only to make the L1 A2A ingress runnable before
 * the real L4 TaskControlClient is integrated. It also emits a few temporary
 * NotificationFrame instances so the L1 A2A egress modes can be locally verified.
 *
 * <p>Delete this class when L4 provides the real {@link TaskHandler} bean.
 */
public final class TemporaryL4TaskHandler implements TaskHandler {

    private final NotificationPort notificationPort;
    private final Executor executor;

    public TemporaryL4TaskHandler(NotificationPort notificationPort, Executor executor) {
        this.notificationPort = Objects.requireNonNull(notificationPort, "notificationPort");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public CompletionStage<AccessAcceptedResponse> runTask(AccessIntent intent) {
        Objects.requireNonNull(intent, "intent");
        String taskId = "temporary-task-" + UUID.randomUUID();
        AccessAcceptedResponse response = new AccessAcceptedResponse(
                intent.tenantId(),
                intent.userId(),
                intent.agentId(),
                intent.sessionId(),
                taskId,
                true,
                "Accepted by temporary L4 TaskHandler placeholder");
        CompletableFuture.runAsync(() -> emitTemporaryFrames(intent, taskId), executor);
        return CompletableFuture.completedFuture(response);
    }

    private void emitTemporaryFrames(AccessIntent intent, String taskId) {
        sleepQuietly(200L);
        notificationPort.notify(new NotificationFrame(
                intent.tenantId(),
                intent.sessionId(),
                taskId,
                NotificationType.ACK,
                Map.of("message", "temporary task accepted"),
                false));
        sleepQuietly(200L);
        notificationPort.notify(new NotificationFrame(
                intent.tenantId(),
                intent.sessionId(),
                taskId,
                NotificationType.LLM_RESULT,
                Map.of("message", "temporary streaming result for " + intent.query()),
                true));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}


