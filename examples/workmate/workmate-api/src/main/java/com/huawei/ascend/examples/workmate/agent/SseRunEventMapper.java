package com.huawei.ascend.examples.workmate.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.approval.ApprovalGate.PendingApproval;
import com.huawei.ascend.examples.workmate.chat.RecordedRunEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseRunEventMapper {

    private final ObjectMapper objectMapper;

    public SseRunEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter.SseEventBuilder trajectoryEvent(TrajectoryEvent event) {
        return switch (event.kind()) {
            case TOOL_CALL_START -> namedEvent("tool.start", Map.of(
                    "toolName", nullToEmpty(event.name()),
                    "args", event.args()));
            case TOOL_CALL_END -> namedEvent("tool.end", Map.of(
                    "toolName", nullToEmpty(event.name()),
                    "result", event.result()));
            case ERROR -> namedEvent("run.error", Map.of(
                    "message", event.error() != null ? nullToEmpty(event.error().message()) : "unknown error"));
            case RUN_END -> namedEvent("run.completed", Map.of());
            default -> null;
        };
    }

    public SseEmitter.SseEventBuilder heartbeat() {
        return SseEmitter.event().comment("heartbeat");
    }

    public SseEmitter.SseEventBuilder fromRecordedEvent(RecordedRunEvent event) {
        return namedEvent(event.eventName(), readPayload(event.payloadJson()), event.seq());
    }

    private Map<String, Object> readPayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return Map.of("raw", payloadJson);
        }
    }

    public SseEmitter.SseEventBuilder messageDelta(String text) {
        return namedEvent("message.delta", Map.of("text", text));
    }

    public SseEmitter.SseEventBuilder runFailed(String message) {
        return namedEvent("run.failed", Map.of("message", message));
    }

    public SseEmitter.SseEventBuilder approvalRequired(PendingApproval pending) {
        return namedEvent("approval.required", approvalPayload(pending));
    }

    public Map<String, Object> approvalPayload(PendingApproval pending) {
        return Map.of(
                "approvalId", pending.id().toString(),
                "sessionId", pending.sessionId().toString(),
                "tool", pending.toolName(),
                "risk", pending.risk().level(),
                "reason", pending.risk().reason(),
                "summary", pending.risk().summary(),
                "args", pending.args());
    }

    public SseEmitter.SseEventBuilder runCompleted() {
        return namedEvent("run.completed", Map.of());
    }

    public SseEmitter.SseEventBuilder artifactAdded(
            String path, String name, String mime, long size, String updatedAt) {
        return namedEvent("artifact.added", Map.of(
                "path", path,
                "name", name,
                "mime", mime,
                "size", size,
                "updatedAt", updatedAt));
    }

    public SseEmitter.SseEventBuilder planCreate(PlanPayload plan) {
        List<Map<String, Object>> steps = plan.steps().stream()
                .map(step -> Map.<String, Object>of(
                        "id", step.id(),
                        "title", step.title(),
                        "status", step.status() != null ? step.status() : "pending"))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", plan.planId());
        if (plan.title() != null && !plan.title().isBlank()) {
            payload.put("title", plan.title());
        }
        payload.put("steps", steps);
        return namedEvent("plan.create", payload);
    }

    public SseEmitter.SseEventBuilder usageDelta(
            int deltaPromptTokens,
            int deltaCompletionTokens,
            long totalPromptTokens,
            long totalCompletionTokens,
            String model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deltaPromptTokens", deltaPromptTokens);
        payload.put("deltaCompletionTokens", deltaCompletionTokens);
        payload.put("totalPromptTokens", totalPromptTokens);
        payload.put("totalCompletionTokens", totalCompletionTokens);
        if (model != null && !model.isBlank()) {
            payload.put("model", model);
        }
        return namedEvent("usage.delta", payload);
    }

    public SseEmitter.SseEventBuilder sseEvent(String name, Map<String, Object> payload, Integer seq) {
        return namedEvent(name, payload, seq);
    }

    private SseEmitter.SseEventBuilder namedEvent(String name, Map<String, Object> payload) {
        return namedEvent(name, payload, null);
    }

    private SseEmitter.SseEventBuilder namedEvent(String name, Map<String, Object> payload, Integer seq) {
        try {
            SseEmitter.SseEventBuilder builder =
                    SseEmitter.event().name(name).data(objectMapper.writeValueAsString(payload));
            if (seq != null) {
                builder.id(String.valueOf(seq));
            }
            return builder;
        } catch (JsonProcessingException ex) {
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(name).data(payload.toString());
            if (seq != null) {
                builder.id(String.valueOf(seq));
            }
            return builder;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
