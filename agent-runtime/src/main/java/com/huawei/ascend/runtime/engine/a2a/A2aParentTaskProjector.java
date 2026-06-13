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
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.a2aproject.sdk.spec.TextPart;

final class A2aParentTaskProjector {
    private static final Logger LOG = LoggerFactory.getLogger(A2aParentTaskProjector.class);
    private static final String WAITING_TARGET_REMOTE_AGENT = "REMOTE_AGENT";
    private static final String REMOTE_TERMINAL_RESULT_MISSING = "{\"error\":\"REMOTE_TERMINAL_RESULT_MISSING\"}";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    boolean isRemoteContinuation(RequestContext ctx) {
        Task task = ctx.getTask();
        if (task == null || task.status() == null
                || task.status().state() != TaskState.TASK_STATE_INPUT_REQUIRED) {
            return false;
        }
        Map<String, Object> metadata = task.metadata();
        return metadata != null
                && WAITING_TARGET_REMOTE_AGENT.equals(String.valueOf(metadata.get("runtime.waitingTarget")));
    }

    RemoteAgentInvocationService.RemoteRoute remoteRoute(Task task) {
        Map<String, Object> metadata = task.metadata() == null ? Map.of() : task.metadata();
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
        if ((result.type() == RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE
                || result.type() == RemoteAgentInvocationService.RemoteAgentResult.Type.ARTIFACT)
                && result.text() != null && !result.text().isBlank()) {
            emitter.addArtifact(List.<Part<?>>of(new TextPart(result.text())));
        }
    }

    void requireRemoteInput(AgentExecutionResult.RemoteInvocation invocation,
            RemoteAgentInvocationService.RemoteAgentResult result, AgentEmitter emitter) {
        Message message = emitter.newAgentMessage(List.<Part<?>>of(new TextPart(safeText(result.text()))), null);
        emitter.emitEvent(TaskStatusUpdateEvent.builder()
                .taskId(emitter.getTaskId())
                .contextId(emitter.getContextId())
                .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED, message, null))
                .metadata(remoteMetadata(invocation, result))
                .build());
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
                    LOG.info("[A2A] remote outcome=completed remoteAgentId={} resultLen={}",
                            invocation.remoteAgentId(), result.text() != null ? result.text().length() : 0);
                    return RemoteOutcome.resumeWith(safeText(result.text()));
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
