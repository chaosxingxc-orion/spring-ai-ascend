package com.huawei.ascend.runtime.engine.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;

final class A2aParentTaskProjector {
    private static final Logger LOG = LoggerFactory.getLogger(A2aParentTaskProjector.class);
    private static final String WAITING_TARGET_REMOTE_AGENT = "REMOTE_AGENT";
    private static final String REMOTE_TERMINAL_RESULT_MISSING = "{\"error\":\"REMOTE_TERMINAL_RESULT_MISSING\"}";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    boolean isRemoteContinuation(RequestContext ctx) {
        Task task = ctx.getTask();
        if (task == null || task.status() == null
                || task.status().state() != TaskState.TASK_STATE_INPUT_REQUIRED) {
            LOG.info("[A2A] remote continuation check: no — task={} state={}",
                    task != null ? task.id() : "null",
                    task != null && task.status() != null ? task.status().state() : "null");
            return false;
        }
        Map<String, Object> taskMd = task.metadata();
        Map<String, Object> msgMd = task.status().message() != null
                ? task.status().message().metadata() : null;
        // Check task-level metadata first (populated by the follow-up
        // TaskStatusUpdateEvent with event-level metadata emitted in
        // requireRemoteInput), then fall back to status-message metadata.
        boolean parked = WAITING_TARGET_REMOTE_AGENT.equals(
                String.valueOf(taskMd != null ? taskMd.get("runtime.waitingTarget") : null))
                || WAITING_TARGET_REMOTE_AGENT.equals(
                        String.valueOf(msgMd != null ? msgMd.get("runtime.waitingTarget") : null));
        LOG.info("[A2A] remote continuation check: taskId={} state={} taskMdHasWaitingTarget={} msgMdHasWaitingTarget={} matched={}",
                task.id(), task.status().state(),
                taskMd != null && taskMd.containsKey("runtime.waitingTarget"),
                msgMd != null && msgMd.containsKey("runtime.waitingTarget"),
                parked);
        return parked;
    }

    RemoteAgentInvocationService.RemoteRoute remoteRoute(Task task) {
        Map<String, Object> metadata = routeMetadata(task);
        requireRouteMetadata(metadata,
                "runtime.remoteAgentId",
                "runtime.remoteTaskId",
                "runtime.remoteContextId",
                "runtime.toolCallId",
                "runtime.localConversationId");
        return new RemoteAgentInvocationService.RemoteRoute(
                string(metadata, "runtime.remoteAgentId"),
                string(metadata, "runtime.remoteTaskId"),
                string(metadata, "runtime.remoteContextId"),
                string(metadata, "runtime.toolCallId"),
                task.id(),
                task.contextId(),
                string(metadata, "runtime.localConversationId"));
    }

    /**
     * Returns the route metadata from the task, checking task-level metadata
     * first (set via event-level metadata on the save-path TaskStatusUpdateEvent),
     * then status-message metadata as a fallback.
     */
    private static Map<String, Object> routeMetadata(Task task) {
        Map<String, Object> taskMd = task.metadata();
        if (taskMd != null && hasText(taskMd.get("runtime.waitingTarget"))) {
            return taskMd;
        }
        if (task.status() != null && task.status().message() != null) {
            Map<String, Object> md = task.status().message().metadata();
            if (md != null && !md.isEmpty()) {
                return md;
            }
        }
        return taskMd == null ? Map.of() : taskMd;
    }

    AgentExecutionResult.RemoteInvocation remoteInvocation(RemoteAgentInvocationService.RemoteRoute route) {
        return new AgentExecutionResult.RemoteInvocation(
                route.remoteAgentId(),
                "",
                route.toolCallId(),
                route.parentTaskId(),
                route.parentContextId(),
                route.localConversationId(),
                Map.of());
    }

    void projectRemoteProgress(RemoteAgentInvocationService.RemoteAgentResult result, AgentEmitter emitter) {
        if (result == null) {
            return;
        }
        String text = result.text();
        LOG.info("[A2A] remote progress type={} target={} textLen={} text={}",
                result.type(), result.target(),
                text != null ? text.length() : 0,
                text);
        // Only forward to end-user when target is USER or BOTH (LLM-only stays internal)
        AgentExecutionResult.Target target = result.target();
        if (target == AgentExecutionResult.Target.LLM) {
            return;
        }
        if ((result.type() == RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE
                || result.type() == RemoteAgentInvocationService.RemoteAgentResult.Type.ARTIFACT)
                && result.text() != null && !result.text().isBlank()) {
            emitter.addArtifact(List.<Part<?>>of(new TextPart(result.text())));
        }
    }

    void requireRemoteInput(AgentExecutionResult.RemoteInvocation invocation,
            RemoteAgentInvocationService.RemoteAgentResult result, AgentEmitter emitter) {
        Map<String, Object> metadata = remoteMetadata(invocation, result);
        // Mark this as a USER-targeted interruption so the parent projector
        // forwards the prompt to the end user, not the LLM.
        metadata.put("a2a.target", AgentExecutionResult.Target.USER.name());
        Message message = emitter.newAgentMessage(
                List.<Part<?>>of(new TextPart(safeText(result.text()))), metadata);
        emitter.requiresInput(message, false);

        // The A2A SDK's AgentEmitter.updateStatus() creates a TaskStatusUpdateEvent
        // without event-level metadata — only the status message carries metadata,
        // and TaskManager.saveTaskEvent() does not propagate message metadata to
        // task-level metadata. Emit a second, save-path-only TaskStatusUpdateEvent
        // whose event-level metadata IS the route info so that TaskManager merges
        // it into task.metadata() on persistence. This event arrives after the
        // requiresInput event in the EventQueue, so the ResultAggregator has
        // already observed the interrupt and the stream is draining; it does not
        // affect the client-visible SSE stream.
        TaskStatusUpdateEvent savePathEvent = TaskStatusUpdateEvent.builder()
                .taskId(invocation.parentTaskId())
                .contextId(invocation.parentContextId())
                .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED, message, null))
                .metadata(metadata)
                .build();
        emitter.emitEvent(savePathEvent);
        LOG.info("[A2A] remote route metadata save-path event emitted taskId={} metadataKeys={}",
                invocation.parentTaskId(), metadata.keySet());
    }

    RemoteOutcome projectRemoteOutcome(AgentExecutionResult.RemoteInvocation invocation,
            List<RemoteAgentInvocationService.RemoteAgentResult> results, AgentEmitter emitter) {
        for (RemoteAgentInvocationService.RemoteAgentResult result : results) {
            switch (result.type()) {
                case MESSAGE, ARTIFACT -> {
                    // Progress is projected by the outbound callback while the remote stream is still open.
                }
                case INPUT_REQUIRED -> {
                    LOG.info("[A2A] remote outcome=input_required remoteAgentId={} remoteTaskId={}",
                            invocation.remoteAgentId(), result.remoteTaskId());
                    requireRemoteInput(invocation, result, emitter);
                    return RemoteOutcome.waitForInput();
                }
                case COMPLETED -> {
                    AgentExecutionResult.Target target = result.target();
                    String text = safeText(result.text());
                    LOG.info("[A2A] remote outcome=completed remoteAgentId={} resultLen={} target={}",
                            invocation.remoteAgentId(), text.length(), target);
                    // target=USER: show to user, empty tool result for LLM
                    // target=LLM: tool result for LLM, don't show to user
                    // target=BOTH: both
                    if (target == AgentExecutionResult.Target.USER
                            || target == AgentExecutionResult.Target.BOTH) {
                        if (!text.isBlank()) {
                            emitter.addArtifact(List.<Part<?>>of(new TextPart(text)));
                        }
                    }
                    if (target == AgentExecutionResult.Target.USER) {
                        return RemoteOutcome.resumeWith("");
                    }
                    return RemoteOutcome.resumeWith(text);
                }
                case FAILED -> {
                    LOG.warn("[A2A] remote outcome=failed remoteAgentId={} error={}",
                            invocation.remoteAgentId(), safeText(result.text()));
                    return RemoteOutcome.resumeWith(
                            errorJson(safeText(result.text()), result.metadata().get("code")));
                }
            }
        }
        LOG.warn("[A2A] remote outcome=no-terminal-result remoteAgentId={}", invocation.remoteAgentId());
        return RemoteOutcome.resumeWith(REMOTE_TERMINAL_RESULT_MISSING);
    }

    AgentExecutionContext remoteResumeContext(RequestContext requestContext, String handlerAgentId,
            AgentExecutionResult.RemoteInvocation invocation, String toolResult) {
        Map<String, Object> variables = Map.of(
                AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, invocation.localConversationId(),
                AgentExecutionContext.REMOTE_TOOL_CALL_ID_VARIABLE, invocation.toolCallId(),
                AgentExecutionContext.REMOTE_TOOL_RESULT_VARIABLE, toolResult);
        return new AgentExecutionContext(
                new RuntimeIdentity(
                        A2aAgentExecutor.metadata(requestContext, A2aAgentExecutor.TENANT_STATE_KEY, "default"),
                        A2aAgentExecutor.metadata(requestContext, "userId", "system"),
                        invocation.parentContextId(),
                        invocation.parentTaskId(),
                        A2aAgentExecutor.metadata(requestContext, "agentId", handlerAgentId)),
                AgentExecutionContext.INPUT_TYPE_REMOTE_RESUME,
                List.of(),
                variables,
                invocation.localConversationId(),
                null);
    }

    private static Map<String, Object> remoteMetadata(AgentExecutionResult.RemoteInvocation invocation,
            RemoteAgentInvocationService.RemoteAgentResult result) {
        Map<String, Object> metadata = new HashMap<>(result.metadata());
        metadata.put("runtime.waitingTarget", WAITING_TARGET_REMOTE_AGENT);
        metadata.put("runtime.remoteInvocationId", invocation.toolCallId());
        metadata.put("runtime.remoteAgentId", invocation.remoteAgentId());
        metadata.put("runtime.remoteTaskId", result.remoteTaskId());
        metadata.put("runtime.remoteContextId", result.remoteContextId());
        metadata.put("runtime.toolCallId", invocation.toolCallId());
        metadata.put("runtime.localConversationId", invocation.localConversationId());
        return metadata;
    }

    private static void requireRouteMetadata(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            if (!hasText(metadata.get(key))) {
                throw new IllegalArgumentException("REMOTE_ROUTE_METADATA_MISSING: " + key);
            }
        }
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    /**
     * Serializes the remote failure into the tool-result JSON via Jackson so error
     * text with control characters or quotes stays parseable downstream; a stable
     * {@code code} from the remote result metadata is passed through so the caller
     * can branch on it (e.g. a timeout) instead of matching free text.
     */
    private static String errorJson(String text, Object code) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", text);
        if (code != null) {
            error.put("code", String.valueOf(code));
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            // A map of strings cannot fail to serialize; surface loudly if it ever does.
            throw new IllegalStateException("failed to serialize remote error result", e);
        }
    }

    private static String string(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    record RemoteOutcome(boolean waitingForRemoteInput, String toolResult) {
        static RemoteOutcome waitForInput() {
            return new RemoteOutcome(true, null);
        }

        static RemoteOutcome resumeWith(String toolResult) {
            return new RemoteOutcome(false, toolResult);
        }
    }
}
