package com.huawei.ascend.service.access.protocol.a2a.egress;

import com.huawei.ascend.service.access.model.AgentNotification;
import com.huawei.ascend.service.access.model.AgentNotification.RunError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

public final class A2aOutputMapper {

    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public A2aOutput toA2aOutput(AgentNotification notification) {
        Objects.requireNonNull(notification, "notification");
        Map<String, Object> metadata = new HashMap<>();
        metadata.putAll(notification.metadata());
        metadata.put("notificationType", notification.type().name());
        metadata.put("runStatus", notification.status().wire());
        metadata.put("sequence", nextSequence(notification));
        metadata.put("protocol", "A2A");
        String kind = outputKind(notification);
        if ("Artifact".equals(kind)) {
            metadata.put("artifactId", artifactId(notification));
        }
        org.a2aproject.sdk.spec.StreamingEventKind event = toStreamingEvent(notification, kind, metadata);
        if (notification.terminal()) {
            sequences.remove(sequenceKey(notification));
        }
        return new A2aOutput(
                kind,
                notification.taskId(),
                event,
                payload(notification),
                notification.terminal(),
                metadata);
    }

    private org.a2aproject.sdk.spec.StreamingEventKind toStreamingEvent(
            AgentNotification notification,
            String kind,
            Map<String, Object> metadata) {
        String contextId = notification.sessionId();
        if ("Artifact".equals(kind)) {
            org.a2aproject.sdk.spec.Artifact artifact = A2aTaskMapper.artifact(
                    metadata.get("artifactId").toString(),
                    outputText(notification),
                    metadata);
            return new TaskArtifactUpdateEvent(
                    notification.taskId(),
                    artifact,
                    contextId,
                    Boolean.TRUE,
                    notification.terminal(),
                    metadata);
        }
        Message message = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(outputText(notification))))
                .messageId(UUID.randomUUID().toString())
                .contextId(contextId)
                .taskId(notification.taskId())
                .metadata(metadata)
                .build();
        if ("Message".equals(kind)) {
            return message;
        }
        TaskState state = taskState(notification);
        TaskStatus status = new TaskStatus(state, message, null);
        return new TaskStatusUpdateEvent(notification.taskId(), status, contextId, metadata);
    }

    private String outputKind(AgentNotification notification) {
        if (notification.error() != null) {
            return "error";
        }
        if (notification.type() == com.huawei.ascend.service.access.model.NotificationType.TOOL_RESULT
                && !notification.output().isEmpty()) {
            return "Artifact";
        }
        if (!notification.output().isEmpty()) {
            return "Message";
        }
        return "TaskStatus";
    }

    private Object payload(AgentNotification notification) {
        return notification.error() == null ? notification.output() : notification.error();
    }

    private String outputText(AgentNotification notification) {
        if (notification.error() != null) {
            RunError error = notification.error();
            return "%s: %s".formatted(error.code(), error.message());
        }
        StringBuilder text = new StringBuilder();
        for (com.huawei.ascend.service.schema.Message message : notification.output()) {
            text.append(message.text());
        }
        return text.toString();
    }

    private TaskState taskState(AgentNotification notification) {
        return switch (notification.status()) {
            case COMPLETED -> TaskState.TASK_STATE_COMPLETED;
            case FAILED, REJECTED -> TaskState.TASK_STATE_FAILED;
            case CANCELED -> TaskState.TASK_STATE_CANCELED;
            default -> notification.terminal() ? TaskState.TASK_STATE_COMPLETED : TaskState.TASK_STATE_WORKING;
        };
    }

    private long nextSequence(AgentNotification notification) {
        return sequences.computeIfAbsent(sequenceKey(notification), ignored -> new AtomicLong()).incrementAndGet();
    }

    private String artifactId(AgentNotification notification) {
        return "artifact-%s-%s-%s".formatted(
                nullToId(notification.tenantId()),
                nullToId(notification.sessionId()),
                nullToId(notification.taskId()));
    }

    private String sequenceKey(AgentNotification notification) {
        return "%s:%s".formatted(notification.tenantId(), notification.sessionId());
    }

    private String nullToId(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
