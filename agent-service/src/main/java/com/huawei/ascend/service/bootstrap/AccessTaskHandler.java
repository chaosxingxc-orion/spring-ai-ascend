package com.huawei.ascend.service.bootstrap;

import com.huawei.ascend.service.access.core.TaskHandler;
import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.model.AccessIntent;
import com.huawei.ascend.service.schema.AgentRequest;
import com.huawei.ascend.service.schema.Message;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.RunTaskCommand;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.TaskAction;
import com.huawei.ascend.service.taskcontrol.api.TaskControlClient.TaskResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * The real inbound glue: turns an {@link AccessIntent} accepted by the access
 * layer into a task on task-centric-control.
 *
 * <p>This replaces the placeholder handler and is the seam the human review
 * found missing — without it the access layer never reached task control. It is
 * a pure access-to-task-control bridge: the access gateway has already
 * pre-allocated the {@code taskId} and bound the reply channel for it, so this
 * handler only translates the intent into the canonical {@link AgentRequest} and
 * submits the run command carrying that id.
 */
public final class AccessTaskHandler implements TaskHandler {

    private final TaskControlClient taskControlClient;

    public AccessTaskHandler(TaskControlClient taskControlClient) {
        this.taskControlClient = Objects.requireNonNull(taskControlClient, "taskControlClient");
    }

    @Override
    public CompletionStage<AccessAcceptedResponse> runTask(AccessIntent intent, String taskId) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(taskId, "taskId");
        AgentRequest request = toAgentRequest(intent);
        RunTaskCommand command = new RunTaskCommand(
                request.tenantId(),
                request.sessionId(),
                taskId,
                request.agentId(),
                TaskAction.RUN,
                request.input(),
                null,
                request.idempotencyKey(),
                request.metadata());
        return taskControlClient.runTask(command).thenApply(result -> toAccepted(intent, result));
    }

    private AgentRequest toAgentRequest(AccessIntent intent) {
        List<Message> input = List.of(Message.user(intent.query() == null ? "" : intent.query()));
        Map<String, Object> metadata = intent.payload() instanceof Map<?, ?> payload
                ? copyStringKeyed(payload) : Map.of();
        return new AgentRequest(
                intent.tenantId(),
                intent.userId(),
                intent.agentId(),
                intent.sessionId(),
                input,
                intent.idempotencyKey(),
                metadata);
    }

    private AccessAcceptedResponse toAccepted(AccessIntent intent, TaskResult result) {
        return new AccessAcceptedResponse(
                result.tenantId(),
                intent.userId(),
                intent.agentId(),
                result.sessionId(),
                result.taskId(),
                result.accepted(),
                result.message());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyStringKeyed(Map<?, ?> source) {
        return source.keySet().stream().allMatch(k -> k instanceof String)
                ? Map.copyOf((Map<String, Object>) source) : Map.of();
    }
}
