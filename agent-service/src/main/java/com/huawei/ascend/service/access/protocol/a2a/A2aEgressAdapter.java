package com.huawei.ascend.service.access.protocol.a2a;

import com.huawei.ascend.service.access.egress.EgressAdapter;
import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.access.model.ReplyChannel;
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

public final class A2aEgressAdapter implements EgressAdapter {

    private final A2aOutputSink outputSink;
    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public A2aEgressAdapter(A2aOutputSink outputSink) {
        this.outputSink = Objects.requireNonNull(outputSink, "outputSink");
    }

    @Override
    public ReplyChannel channel() {
        return ReplyChannel.A2A;
    }

    @Override
    public void deliver(EgressBinding binding, NotificationFrame frame) {
        outputSink.send(binding, toA2aOutput(binding, frame));
        if (frame.terminal()) {
            sequences.remove(sequenceKey(binding));
        }
    }

    public A2aOutput toA2aOutput(NotificationFrame frame) {
        return toA2aOutput(null, frame);
    }

    public A2aOutput toA2aOutput(EgressBinding binding, NotificationFrame frame) {
        String kind = switch (frame.type()) {
            case ACK -> "TaskStatus";
            case TOOL_RESULT -> "Artifact";
            case LLM_RESULT -> "Message";
            case ERROR -> "error";
        };
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("notificationType", frame.type().name());
        metadata.put("sequence", nextSequence(binding, frame));
        metadata.put("protocol", "A2A");
        if ("Artifact".equals(kind)) {
            metadata.put("artifactId", artifactId(binding, frame));
        }
        org.a2aproject.sdk.spec.StreamingEventKind event = toStreamingEvent(binding, frame, kind, metadata);
        return new A2aOutput(
                kind,
                frame.taskId(),
                event,
                frame.payload(),
                frame.terminal(),
                metadata);
    }

    private org.a2aproject.sdk.spec.StreamingEventKind toStreamingEvent(
            EgressBinding binding,
            NotificationFrame frame,
            String kind,
            Map<String, Object> metadata) {
        String contextId = frame.sessionId();
        if ("Artifact".equals(kind)) {
            org.a2aproject.sdk.spec.Artifact artifact = A2aTaskMapper.artifact(
                    metadata.get("artifactId").toString(),
                    String.valueOf(frame.payload()),
                    metadata);
            return new TaskArtifactUpdateEvent(
                    frame.taskId(),
                    artifact,
                    contextId,
                    Boolean.TRUE,
                    frame.terminal(),
                    metadata);
        }
        Message message = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(String.valueOf(frame.payload()))))
                .messageId(UUID.randomUUID().toString())
                .contextId(contextId)
                .taskId(frame.taskId())
                .metadata(metadata)
                .build();
        if ("Message".equals(kind)) {
            return message;
        }
        TaskState state = switch (frame.type()) {
            case ACK -> TaskState.TASK_STATE_SUBMITTED;
            case ERROR -> TaskState.TASK_STATE_FAILED;
            case TOOL_RESULT, LLM_RESULT -> frame.terminal()
                    ? TaskState.TASK_STATE_COMPLETED
                    : TaskState.TASK_STATE_WORKING;
        };
        TaskStatus status = new TaskStatus(state, message, null);
        return new TaskStatusUpdateEvent(frame.taskId(), status, contextId, metadata);
    }

    private long nextSequence(EgressBinding binding, NotificationFrame frame) {
        String key = binding == null
                ? "%s:%s:%s".formatted(frame.tenantId(), frame.sessionId(), frame.taskId())
                : sequenceKey(binding);
        return sequences.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    private String artifactId(EgressBinding binding, NotificationFrame frame) {
        String tenantId = binding == null ? frame.tenantId() : binding.tenantId();
        String sessionId = binding == null ? frame.sessionId() : binding.sessionId();
        String taskId = binding == null ? frame.taskId() : binding.taskId();
        return "artifact-%s-%s-%s".formatted(nullToId(tenantId), nullToId(sessionId), nullToId(taskId));
    }

    private String sequenceKey(EgressBinding binding) {
        return "%s:%s:%s".formatted(binding.tenantId(), binding.sessionId(), binding.taskId());
    }

    private String nullToId(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}


