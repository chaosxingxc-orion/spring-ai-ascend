package com.huawei.ascend.examples.workmate.acp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts ACP {@code sessionUpdate} envelopes to WorkMate {@code run_events} drafts (W38 Phase 2).
 * Inverse of {@link AcpOutboundConverter} for interchange / CLI ingress.
 */
public class AcpInboundConverter {

    public RunEventDraft convert(Map<String, Object> update) {
        if (update == null || update.isEmpty()) {
            return null;
        }
        String sessionUpdate = stringValue(update.get("sessionUpdate"));
        if (sessionUpdate == null || sessionUpdate.isBlank()) {
            return null;
        }
        Map<String, Object> content = asMap(update.get("content"));
        Map<String, Object> meta = asMap(update.get("_meta"));
        Map<String, Object> memberBase = applyMemberFields(new LinkedHashMap<>(), meta);

        return switch (sessionUpdate) {
            case "agent_message_chunk" -> {
                Map<String, Object> delta = applyMemberFields(new LinkedHashMap<>(), meta);
                delta.put("text", stringValue(content.get("text")));
                yield new RunEventDraft("message.delta", delta);
            }
            case "reasoning", "reasoning_text" -> {
                Map<String, Object> reasoning = applyMemberFields(new LinkedHashMap<>(), meta);
                reasoning.put("text", stringValue(content.get("text")));
                yield new RunEventDraft("reasoning.delta", reasoning);
            }
            case "tool_call" -> new RunEventDraft("tool.start", toolStartPayload(content, memberBase));
            case "tool_call_update" -> convertToolCallUpdate(content, memberBase);
            case "open_result_view" -> new RunEventDraft("artifact.added", artifactPayload(content));
            case "user" -> {
                Map<String, Object> user = applyMemberFields(new LinkedHashMap<>(), meta);
                user.put("text", stringValue(content.get("text")));
                yield new RunEventDraft("message.user", user);
            }
            case "session_info_update" -> convertSessionInfoUpdate(content, memberBase);
            default -> null;
        };
    }

    private RunEventDraft convertToolCallUpdate(Map<String, Object> content, Map<String, Object> payload) {
        if ("waiting".equals(stringValue(content.get("status")))) {
            Map<String, Object> approval = new LinkedHashMap<>(payload);
            approval.put("toolName", stringValue(content.get("toolName")));
            approval.put("toolCallId", stringValue(content.get("toolCallId")));
            approval.put("approvalId", stringValue(content.get("approvalId")));
            approval.put("risk", stringValue(content.get("risk")));
            approval.put("summary", stringValue(content.get("summary")));
            if (content.get("args") != null) {
                approval.put("args", content.get("args"));
            }
            return new RunEventDraft("approval.required", approval);
        }
        Map<String, Object> end = new LinkedHashMap<>(payload);
        end.put("toolName", stringValue(content.get("toolName")));
        end.put("toolCallId", stringValue(content.get("toolCallId")));
        end.put("result", content.get("result"));
        return new RunEventDraft("tool.end", end);
    }

    private RunEventDraft convertSessionInfoUpdate(Map<String, Object> content, Map<String, Object> memberBase) {
        if (content.containsKey("plan")) {
            Map<String, Object> plan = asMap(content.get("plan"));
            String eventName = plan.containsKey("planId") || plan.containsKey("steps") ? "plan.update" : "plan.create";
            return new RunEventDraft(eventName, new LinkedHashMap<>(plan));
        }
        Object teamUpdate = content.get("teamUpdate");
        if (teamUpdate instanceof Map<?, ?> teamUpdateMap) {
            String type = stringValue(teamUpdateMap.get("type"));
            Map<String, Object> teamPayload = applyMemberFields(
                    new LinkedHashMap<>(asMap(teamUpdateMap.get("payload"))), memberBase);
            if ("team_created".equals(type)) {
                return new RunEventDraft("team.started", teamPayload);
            }
            if ("member_status_change".equals(type)) {
                return new RunEventDraft("team.member.completed", teamPayload);
            }
        }
        if ("completed".equals(stringValue(content.get("status")))) {
            return new RunEventDraft("run.completed", new LinkedHashMap<>(memberBase));
        }
        if ("failed".equals(stringValue(content.get("status")))) {
            Map<String, Object> failed = new LinkedHashMap<>(memberBase);
            failed.put("message", stringValue(content.get("message")));
            return new RunEventDraft("run.failed", failed);
        }
        if (content.containsKey("teamEvent")) {
            return new RunEventDraft(stringValue(content.get("teamEvent")), asMap(content.get("payload")));
        }
        return null;
    }

    private static Map<String, Object> toolStartPayload(Map<String, Object> content, Map<String, Object> payload) {
        Map<String, Object> start = new LinkedHashMap<>(payload);
        start.put("toolName", stringValue(content.get("toolName")));
        start.put("toolCallId", stringValue(content.get("toolCallId")));
        if (content.get("args") != null) {
            start.put("args", content.get("args"));
        }
        return start;
    }

    private static Map<String, Object> artifactPayload(Map<String, Object> content) {
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("path", stringValue(content.get("path")));
        artifact.put("name", stringValue(content.get("name")));
        artifact.put("mime", stringValue(content.get("mime")));
        if (Boolean.TRUE.equals(content.get("openInPanel"))) {
            artifact.put("openInPanel", true);
            artifact.put("preferredTab", stringValue(content.get("preferredTab")));
        }
        return artifact;
    }

    private static Map<String, Object> applyMemberFields(Map<String, Object> payload, Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) {
            return payload;
        }
        if ("team".equals(stringValue(meta.get("surface")))) {
            payload.put("surface", "team");
        }
        String memberEvent = stringValue(meta.get(AcpMetaKeys.MEMBER_EVENT));
        if (memberEvent != null) {
            payload.putIfAbsent("memberName", memberEvent);
        }
        String memberId = stringValue(meta.get(AcpMetaKeys.MEMBER_ID));
        if (memberId != null) {
            payload.put("memberId", memberId);
        }
        String parentRunId = stringValue(meta.get(AcpMetaKeys.PARENT_RUN_ID));
        if (parentRunId != null) {
            payload.put("parentRunId", parentRunId);
        }
        if (payload.get("memberId") != null && payload.get("parentRunId") == null) {
            payload.putIfAbsent("parentRunId", "member-run");
        }
        return payload;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
