package com.huawei.ascend.service.access.protocol.a2a.egress;

import com.huawei.ascend.service.access.protocol.a2a.model.A2aAcceptedResponse;
import com.huawei.ascend.service.access.protocol.a2a.model.A2aTaskQueryParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

public final class A2aTaskMapper {

    private A2aTaskMapper() {
    }

    public static Task toTask(A2aTaskQueryParams query, List<A2aOutput> outputs) {
        List<Artifact> artifacts = new ArrayList<>();
        List<Message> history = new ArrayList<>();
        TaskStatus status = new TaskStatus(TaskState.TASK_STATE_SUBMITTED);
        for (A2aOutput output : outputs) {
            if (output.event() instanceof TaskArtifactUpdateEvent artifactEvent) {
                artifacts.add(artifactEvent.artifact());
            } else if (output.event() instanceof TaskStatusUpdateEvent statusEvent) {
                status = statusEvent.status();
                if (status.message() != null) {
                    history.add(status.message());
                }
            } else if (output.event() instanceof Message message) {
                history.add(message);
                status = new TaskStatus(
                        output.terminal() ? TaskState.TASK_STATE_COMPLETED : TaskState.TASK_STATE_WORKING,
                        message,
                        null);
            }
        }
        return Task.builder()
                .id(query.taskId())
                .contextId(query.sessionId())
                .status(status)
                .artifacts(artifacts)
                .history(history)
                .metadata(Map.of("tenantId", query.tenantId()))
                .build();
    }

    public static Task canceledTask(A2aAcceptedResponse accepted) {
        Message message = agentMessage(
                accepted.sessionId(),
                accepted.taskId(),
                accepted.message() == null ? "cancel requested" : accepted.message(),
                Map.of(
                        "tenantId", accepted.tenantId(),
                        "userId", accepted.userId(),
                        "agentId", accepted.agentId()));
        return Task.builder()
                .id(accepted.taskId())
                .contextId(accepted.sessionId())
                .status(new TaskStatus(TaskState.TASK_STATE_CANCELED, message, null))
                .history(message)
                .metadata(Map.of("tenantId", accepted.tenantId()))
                .build();
    }

    public static Message agentMessage(String contextId, String taskId, String text, Map<String, Object> metadata) {
        List<Part<?>> parts = List.of(new TextPart(text));
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(parts)
                .messageId(UUID.randomUUID().toString())
                .contextId(contextId)
                .taskId(taskId)
                .metadata(metadata)
                .build();
    }

    static Artifact artifact(String artifactId, String text, Map<String, Object> metadata) {
        return Artifact.builder()
                .artifactId(artifactId)
                .name("agent-output")
                .parts(List.of(new TextPart(text)))
                .metadata(metadata)
                .build();
    }
}
