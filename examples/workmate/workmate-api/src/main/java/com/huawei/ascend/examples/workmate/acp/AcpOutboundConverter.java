package com.huawei.ascend.examples.workmate.acp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts WorkMate {@code run_events} log rows to ACP {@code sessionUpdate} shapes (W38 Phase 1).
 * Does not replace run_events — read-only projection for replay / debug / share export.
 */
public class AcpOutboundConverter {

    public List<Map<String, Object>> convertEventLog(List<Map<String, Object>> eventLog) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (eventLog == null) {
            return result;
        }
        for (Map<String, Object> entry : eventLog) {
            AcpSessionUpdate update = convertEntry(entry);
            if (update != null) {
                result.add(update.toMap());
            }
        }
        return result;
    }

    AcpSessionUpdate convertEntry(Map<String, Object> entry) {
        if (entry == null) {
            return null;
        }
        String name = stringValue(entry.get("name"));
        if (name == null || name.isBlank()) {
            return null;
        }
        Map<String, Object> data = asMap(entry.get("data"));
        int seq = entry.get("seq") instanceof Number number ? number.intValue() : -1;
        Map<String, Object> baseMeta = metaWithOffset(seq, data);

        return switch (name) {
            case "message.delta" -> new AcpSessionUpdate(
                    "agent_message_chunk",
                    mapOf("text", stringValue(data.get("text"))),
                    withMemberMeta(baseMeta, data));
            case "tool.start" -> new AcpSessionUpdate(
                    "tool_call",
                    toolCallContent(data, false),
                    withMemberMeta(baseMeta, data));
            case "tool.end" -> new AcpSessionUpdate(
                    "tool_call_update",
                    toolCallUpdateContent(data),
                    withMemberMeta(baseMeta, data));
            case "approval.required" -> new AcpSessionUpdate(
                    "tool_call_update",
                    approvalWaitingContent(data),
                    baseMeta);
            case "question.required" -> new AcpSessionUpdate(
                    "tool_call_update",
                    questionWaitingContent(data),
                    baseMeta);
            case "plan.create", "plan.update" -> new AcpSessionUpdate(
                    "session_info_update",
                    mapOf("plan", data),
                    baseMeta);
            case "artifact.added" -> new AcpSessionUpdate(
                    "open_result_view",
                    artifactContent(data),
                    baseMeta);
            case "team.started" -> new AcpSessionUpdate(
                    "session_info_update",
                    mapOf("teamUpdate", mapOf("type", "team_created", "payload", data)),
                    baseMeta);
            case "team.member.started", "team.member.completed", "team.member.failed" -> new AcpSessionUpdate(
                    "session_info_update",
                    mapOf("teamUpdate", mapOf("type", "member_status_change", "payload", data)),
                    withMemberMeta(baseMeta, data));
            case "team.completed", "run.completed" -> new AcpSessionUpdate(
                    "session_info_update",
                    mapOf("status", "completed"),
                    metaWithStatus(baseMeta, "completed"));
            case "run.failed", "run.error" -> new AcpSessionUpdate(
                    "session_info_update",
                    mapOf("status", "failed", "message", stringValue(data.get("message"))),
                    metaWithStatus(baseMeta, "failed"));
            case "reasoning.delta" -> new AcpSessionUpdate(
                    "reasoning",
                    mapOf("text", stringValue(data.get("text"))),
                    withMemberMeta(baseMeta, data));
            default -> name.startsWith("team.")
                    ? new AcpSessionUpdate("session_info_update", mapOf("teamEvent", name, "payload", data), withMemberMeta(baseMeta, data))
                    : null;
        };
    }

    private static Map<String, Object> toolCallContent(Map<String, Object> data, boolean waiting) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("toolName", stringValue(data.get("toolName")));
        content.put("toolCallId", stringValue(data.get("toolCallId")));
        if (data.get("args") != null) {
            content.put("args", data.get("args"));
        }
        if (waiting) {
            content.put("status", "waiting");
        }
        return content;
    }

    private static Map<String, Object> toolCallUpdateContent(Map<String, Object> data) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("toolName", stringValue(data.get("toolName")));
        content.put("toolCallId", stringValue(data.get("toolCallId")));
        content.put("result", data.get("result"));
        content.put("status", "completed");
        return content;
    }

    private static Map<String, Object> approvalWaitingContent(Map<String, Object> data) {
        Map<String, Object> content = toolCallContent(data, true);
        content.put("approvalId", stringValue(data.get("approvalId")));
        content.put("risk", stringValue(data.get("risk")));
        content.put("summary", stringValue(data.get("summary")));
        return content;
    }

    private static Map<String, Object> questionWaitingContent(Map<String, Object> data) {
        Map<String, Object> content = toolCallContent(data, true);
        content.put("questionId", stringValue(data.get("questionId")));
        content.put("question", stringValue(data.get("question")));
        content.put("options", data.get("options"));
        content.put("allowFreeText", data.get("allowFreeText"));
        content.put("multiSelect", data.get("multiSelect"));
        return content;
    }

    private static Map<String, Object> artifactContent(Map<String, Object> data) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("path", stringValue(data.get("path")));
        content.put("name", stringValue(data.get("name")));
        content.put("mime", stringValue(data.get("mime")));
        if (Boolean.TRUE.equals(data.get("openInPanel"))) {
            content.put("openInPanel", true);
            content.put("preferredTab", stringValue(data.get("preferredTab")));
        }
        return content;
    }

    private static Map<String, Object> metaWithOffset(int seq, Map<String, Object> data) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (seq >= 0) {
            meta.put(AcpMetaKeys.OFFSET, seq);
        }
        meta.put(AcpMetaKeys.MODE, "history");
        Object topic = data.get("topic");
        if (topic instanceof String topicStr && !topicStr.isBlank()) {
            meta.put(AcpMetaKeys.TOPIC, topicStr);
        }
        return meta;
    }

    private static Map<String, Object> metaWithStatus(Map<String, Object> baseMeta, String status) {
        Map<String, Object> meta = new LinkedHashMap<>(baseMeta);
        meta.put(AcpMetaKeys.STATUS, status);
        return meta;
    }

    private static Map<String, Object> withMemberMeta(Map<String, Object> baseMeta, Map<String, Object> data) {
        String memberId = stringValue(data.get("memberId"));
        String parentRunId = stringValue(data.get("parentRunId"));
        if (memberId == null || parentRunId == null) {
            return baseMeta;
        }
        Map<String, Object> meta = new LinkedHashMap<>(baseMeta);
        String memberName = stringValue(data.get("memberName"));
        meta.put(AcpMetaKeys.MEMBER_EVENT, memberName != null ? memberName : memberId);
        meta.put(AcpMetaKeys.MEMBER_ID, memberId);
        meta.put(AcpMetaKeys.PARENT_RUN_ID, parentRunId);
        meta.put("surface", "team");
        return meta;
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

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
