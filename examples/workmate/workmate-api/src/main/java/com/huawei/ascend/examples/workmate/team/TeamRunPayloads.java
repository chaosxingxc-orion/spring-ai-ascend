package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TeamRunPayloads {

    public static final int SUMMARY_MAX = 500;

    private TeamRunPayloads() {
    }

    public static Map<String, Object> memberPayload(
            String parentRunId, String subRunId, TeamMemberDefinition member) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", member.id());
        payload.put("memberName", member.name());
        payload.put("expertId", member.expertId());
        payload.put("parentRunId", parentRunId);
        payload.put("subRunId", subRunId);
        if (member.role() != null && !member.role().isBlank()) {
            payload.put("role", member.role());
        }
        payload.put("order", member.order());
        if (member.avatar() != null && !member.avatar().isBlank()) {
            payload.put("avatar", member.avatar());
        }
        if (member.participantRole() != null && !member.participantRole().isBlank()) {
            payload.put("participantRole", member.participantRole());
        }
        return payload;
    }

    public static List<Map<String, Object>> memberRoster(List<TeamMemberDefinition> members) {
        List<Map<String, Object>> roster = new ArrayList<>();
        for (TeamMemberDefinition member : members) {
            roster.add(memberRosterEntry(member));
        }
        return roster;
    }

    public static Map<String, Object> memberRosterEntry(TeamMemberDefinition member) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("memberId", member.id());
        entry.put("memberName", member.name());
        entry.put("order", member.order());
        if (member.role() != null && !member.role().isBlank()) {
            entry.put("role", member.role());
        }
        if (member.avatar() != null && !member.avatar().isBlank()) {
            entry.put("avatar", member.avatar());
        }
        if (member.participantRole() != null && !member.participantRole().isBlank()) {
            entry.put("participantRole", member.participantRole());
        }
        return entry;
    }

    public static Map<String, Object> iterationPayload(
            String teamId, String parentRunId, int iteration, int maxIterations) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teamId", teamId);
        payload.put("parentRunId", parentRunId);
        payload.put("iteration", iteration);
        payload.put("maxIterations", maxIterations);
        return payload;
    }

    public static Map<String, Object> withSummary(Map<String, Object> payload, String summary) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.put("summary", truncate(summary, SUMMARY_MAX));
        return copy;
    }

    public static Map<String, Object> withError(Map<String, Object> payload, String error) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.put("error", error != null ? error : "member run failed");
        return copy;
    }

    public static Map<String, Object> withFeedback(Map<String, Object> payload, String feedback) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.put("feedback", truncate(feedback, SUMMARY_MAX));
        return copy;
    }

    public static Map<String, Object> withProgrammaticFeedback(Map<String, Object> payload, String feedback) {
        Map<String, Object> copy = withFeedback(payload, feedback);
        copy.put("programmatic", true);
        return copy;
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max) + "…";
    }
}
