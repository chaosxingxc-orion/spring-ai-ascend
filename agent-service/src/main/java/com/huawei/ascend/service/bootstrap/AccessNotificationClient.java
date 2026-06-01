package com.huawei.ascend.service.bootstrap;

import com.huawei.ascend.service.access.api.NotificationPort;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.access.model.NotificationType;
import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineFailedEvent;
import com.huawei.ascend.service.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.service.engine.event.EngineOutputEvent;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineOutput;
import com.huawei.ascend.service.engine.spi.AccessLayerClient;

import java.util.Objects;

/**
 * The real outbound glue: implements the engine's {@link AccessLayerClient} port
 * by translating engine execution events into access-layer
 * {@link NotificationFrame}s and publishing them through the
 * {@link NotificationPort}.
 *
 * <p>This is the second seam the human review found missing. Because the engine
 * bean is {@code @ConditionalOnBean(AccessLayerClient.class)}, without this
 * implementation the engine never activated and output never returned to the
 * caller.
 *
 * <p>Type mapping: incremental output and successful completion both carry
 * model text, so they map to {@link NotificationType#LLM_RESULT} (completion is
 * marked terminal); failure maps to {@link NotificationType#ERROR} (terminal);
 * an interrupt requesting user input maps to {@link NotificationType#ACK}
 * (non-terminal) so the caller can supply more input.
 */
public final class AccessNotificationClient implements AccessLayerClient {

    private final NotificationPort notificationPort;

    public AccessNotificationClient(NotificationPort notificationPort) {
        this.notificationPort = Objects.requireNonNull(notificationPort, "notificationPort");
    }

    @Override
    public void appendOutput(EngineExecutionScope scope, EngineOutputEvent event) {
        EngineOutput output = event == null ? null : event.getOutput();
        boolean terminal = output != null && output.isFinalOutput();
        publish(scope, NotificationType.LLM_RESULT, text(output), terminal);
    }

    @Override
    public void completeOutput(EngineExecutionScope scope, EngineCompletedEvent event) {
        EngineOutput output = event == null ? null : event.getFinalOutput();
        publish(scope, NotificationType.LLM_RESULT, text(output), true);
    }

    @Override
    public void failOutput(EngineExecutionScope scope, EngineFailedEvent event) {
        String code = event == null ? "UNKNOWN" : event.getErrorCode();
        String message = event == null ? "" : event.getErrorMessage();
        publish(scope, NotificationType.ERROR, new ErrorPayload(code, message), true);
    }

    @Override
    public void requestUserInput(EngineExecutionScope scope, EngineInterruptedEvent event) {
        String prompt = event == null ? null : event.getPrompt();
        publish(scope, NotificationType.ACK, prompt, false);
    }

    private void publish(EngineExecutionScope scope, NotificationType type, Object payload, boolean terminal) {
        Objects.requireNonNull(scope, "scope");
        String sessionId = scope.sessionId() == null ? scope.taskId() : scope.sessionId();
        notificationPort.notify(new NotificationFrame(
                scope.tenantId(), sessionId, scope.taskId(), type, payload, terminal));
    }

    private static String text(EngineOutput output) {
        return output == null || output.getContent() == null ? "" : output.getContent();
    }

    /** Structured error payload carried on an {@link NotificationType#ERROR} frame. */
    public record ErrorPayload(String code, String message) {
    }
}
