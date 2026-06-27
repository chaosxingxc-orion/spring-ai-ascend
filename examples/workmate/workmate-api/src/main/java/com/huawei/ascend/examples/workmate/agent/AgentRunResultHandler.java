package com.huawei.ascend.examples.workmate.agent;

import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.usage.SessionUsageService;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AgentRunResultHandler {

    private final SessionPersistenceService sessionPersistenceService;
    private final SessionUsageService sessionUsageService;
    private final RunEventEmitter runEventEmitter;

    public AgentRunResultHandler(
            SessionPersistenceService sessionPersistenceService,
            SessionUsageService sessionUsageService,
            RunEventEmitter runEventEmitter) {
        this.sessionPersistenceService = sessionPersistenceService;
        this.sessionUsageService = sessionUsageService;
        this.runEventEmitter = runEventEmitter;
    }

    public void handleResult(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext persistenceContext,
            AgentExecutionResult result,
            AtomicBoolean terminalEventSent,
            boolean streamAssistant,
            boolean emitTerminalEvents,
            StringBuilder outputCollector) {
        if (result == null) {
            return;
        }
        switch (result.type()) {
            case OUTPUT -> {
                if (result.outputContent() != null && !result.outputContent().isBlank()) {
                    if (streamAssistant) {
                        sessionPersistenceService.appendAssistantDelta(persistenceContext, result.outputContent());
                        runEventEmitter.emit(emitter, clientConnected, persistenceContext, "message.delta",
                                Map.of("text", result.outputContent()));
                    } else {
                        outputCollector.append(result.outputContent());
                    }
                }
            }
            case COMPLETED -> {
                if (result.outputContent() != null && !result.outputContent().isBlank()) {
                    if (streamAssistant) {
                        String completed = result.outputContent();
                        String streamed = persistenceContext.currentTurnText();
                        String remaining = (streamed != null && !streamed.isEmpty()
                                && completed.startsWith(streamed))
                                ? completed.substring(streamed.length())
                                : (completed.equals(streamed) ? "" : completed);
                        if (!remaining.isEmpty()) {
                            sessionPersistenceService.appendAssistantDelta(persistenceContext, remaining);
                            runEventEmitter.emit(emitter, clientConnected, persistenceContext, "message.delta",
                                    Map.of("text", remaining));
                        }
                    } else {
                        outputCollector.append(result.outputContent());
                    }
                }
            }
            case FAILED -> {
                String message = result.errorMessage() != null ? result.errorMessage() : result.errorCode();
                sessionPersistenceService.recordSystemMessage(persistenceContext, message, "error");
                if (emitTerminalEvents) {
                    runEventEmitter.emit(emitter, clientConnected, persistenceContext, "run.failed", Map.of("message", message));
                    terminalEventSent.set(true);
                }
            }
            case INTERRUPTED -> {
                String message = "Run interrupted: "
                        + (result.prompt() != null ? result.prompt() : "approval or remote resume pending");
                sessionPersistenceService.recordSystemMessage(persistenceContext, message, "error");
                if (emitTerminalEvents) {
                    runEventEmitter.emit(emitter, clientConnected, persistenceContext, "run.failed", Map.of("message", message));
                    terminalEventSent.set(true);
                }
            }
            default -> {
            }
        }
    }

    public void emitTrajectory(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext context,
            TrajectoryEvent event,
            boolean streamAssistant) {
        switch (event.kind()) {
            case TOOL_CALL_START -> {
                String toolCallId = sessionPersistenceService.recordToolStart(
                        context, nullToEmpty(event.name()), event.args());
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("toolName", nullToEmpty(event.name()));
                payload.put("args", event.args());
                if (toolCallId != null) {
                    payload.put("toolCallId", toolCallId);
                }
                runEventEmitter.emit(emitter, clientConnected, context, "tool.start", payload);
            }
            case TOOL_CALL_END -> {
                String toolCallId = sessionPersistenceService.recordToolEnd(
                        context,
                        nullToEmpty(event.name()),
                        event.result(),
                        isToolFailure(event.result()));
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("toolName", nullToEmpty(event.name()));
                payload.put("result", event.result());
                if (toolCallId != null) {
                    payload.put("toolCallId", toolCallId);
                }
                runEventEmitter.emit(emitter, clientConnected, context, "tool.end", payload);
            }
            case ERROR -> {
                String message = event.error() != null ? nullToEmpty(event.error().message()) : "unknown error";
                runEventEmitter.emit(emitter, clientConnected, context, "run.error", Map.of("message", message));
            }
            case RUN_END -> {
                if (streamAssistant && context.memberId() == null) {
                    runEventEmitter.emit(emitter, clientConnected, context, "run.completed", Map.of());
                }
            }
            case REASONING -> {
                String reasoning = event.reasoning();
                if (reasoning != null && !reasoning.isBlank()) {
                    runEventEmitter.emit(emitter, clientConnected, context, "reasoning.delta", Map.of("text", reasoning));
                }
            }
            default -> {
            }
        }
    }

    public void emitUsageDeltaIfPresent(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext context,
            UUID sessionId,
            String taskId,
            TrajectoryEvent event) {
        if (event.kind() != TrajectoryEvent.Kind.MODEL_CALL_END || event.usage() == null) {
            return;
        }
        SessionUsageService.SessionUsageDelta delta =
                sessionUsageService.recordModelUsage(sessionId, taskId, event.usage());
        if (delta == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deltaPromptTokens", delta.deltaPromptTokens());
        payload.put("deltaCompletionTokens", delta.deltaCompletionTokens());
        payload.put("totalPromptTokens", delta.totalPromptTokens());
        payload.put("totalCompletionTokens", delta.totalCompletionTokens());
        payload.put("runId", taskId);
        if (delta.model() != null && !delta.model().isBlank()) {
            payload.put("model", delta.model());
        }
        runEventEmitter.emit(emitter, clientConnected, context, "usage.delta", payload);
    }

    private static boolean isToolFailure(Object result) {
        if (result == null || !(result instanceof Map<?, ?> map)) {
            return false;
        }
        Object success = map.get("success");
        if (Boolean.FALSE.equals(success)) {
            return true;
        }
        Object data = map.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return Boolean.FALSE.equals(dataMap.get("success"));
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
