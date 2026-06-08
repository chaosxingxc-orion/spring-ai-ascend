package com.huawei.ascend.runtime.access.output;

import com.huawei.ascend.runtime.access.api.NotificationPort;
import com.huawei.ascend.runtime.access.AgentNotification;
import com.huawei.ascend.runtime.access.AgentNotification.RunError;
import com.huawei.ascend.runtime.access.NotificationType;
import com.huawei.ascend.runtime.engine.EngineEvent;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.EngineOutput;
import com.huawei.ascend.runtime.engine.AccessLayerClient;
import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.common.RunStatus;
import com.huawei.ascend.runtime.common.Timing;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real outbound glue: implements the engine's {@link AccessLayerClient} port
 * by translating engine execution events into access-layer
 * {@link AgentNotification}s and publishing them through the
 * {@link NotificationPort}.
 *
 * <p>Type mapping: incremental output and successful completion both carry
 * model text, so they map to {@link NotificationType#LLM_RESULT} (completion is
 * marked terminal); failure maps to {@link NotificationType#ERROR} (terminal);
 * an interrupt requesting user input maps to {@link NotificationType#ACK}
 * (non-terminal) so the caller can supply more input.
 */
public final class EngineOutputSink implements AccessLayerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineOutputSink.class);

    private final NotificationPort notificationPort;

    public EngineOutputSink(NotificationPort notificationPort) {
        this.notificationPort = Objects.requireNonNull(notificationPort, "notificationPort");
    }

    @Override
    public void appendOutput(EngineExecutionScope scope, EngineEvent event) {
        EngineOutput output = event == null ? null : event.output();
        boolean terminal = output != null && output.isFinalOutput();
        RunStatus status = terminal ? RunStatus.COMPLETED : RunStatus.IN_PROGRESS;
        publish(scope, NotificationType.LLM_RESULT, status, messages(text(output)), null, Map.of(), terminal);
    }

    @Override
    public void completeOutput(EngineExecutionScope scope, EngineEvent event) {
        EngineOutput output = event == null ? null : event.output();
        publish(scope, NotificationType.LLM_RESULT, RunStatus.COMPLETED,
                messages(text(output)), null, Map.of(), true);
    }

    @Override
    public void failOutput(EngineExecutionScope scope, EngineEvent event) {
        String code = event == null ? "UNKNOWN" : event.errorCode();
        String message = event == null ? "" : event.errorMessage();
        publish(scope, NotificationType.ERROR, RunStatus.FAILED,
                List.of(), new RunError(code, message), Map.of(), true);
    }

    @Override
    public void requestUserInput(EngineExecutionScope scope, EngineEvent event) {
        String prompt = event == null ? null : event.prompt();
        publish(scope, NotificationType.ACK, RunStatus.INCOMPLETE,
                messages(prompt), null, Map.of("waitingReason", "USER_INPUT"), false);
    }

    private void publish(EngineExecutionScope scope, NotificationType type, RunStatus status, List<Message> output,
                         RunError error, Map<String, Object> metadata, boolean terminal) {
        long startedNanos = System.nanoTime();
        Objects.requireNonNull(scope, "scope");
        String sessionId = scope.sessionId() == null ? scope.taskId() : scope.sessionId();
        notificationPort.notify(new AgentNotification(
                scope.tenantId(), sessionId, scope.taskId(), type, status, output, error, metadata, terminal));
        LOGGER.info("trace stage=access-notification-publish tenantId={} sessionId={} taskId={} type={} status={} terminal={} durationMs={}",
                scope.tenantId(),
                sessionId,
                scope.taskId(),
                type,
                status,
                terminal,
                Timing.elapsedMs(startedNanos));
    }

    private static String text(EngineOutput output) {
        return output == null || output.getContent() == null ? "" : output.getContent();
    }

    private static List<Message> messages(String text) {
        return List.of(Message.assistant(text == null ? "" : text));
    }
}
