package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.TeamLeadDefinition;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.team.mailbox.TeamMailboxAddressing;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TeamEventSseAdapter {

    private static final int SUMMARY_MAX = 500;

    private TeamEventSseAdapter() {
    }

    /** Whether an SSE event name is a member lifecycle signal (owned by {@link MemberWorker} on async paths). */
    public static boolean isMemberLifecycleEvent(String sseEventName) {
        if (sseEventName == null || !sseEventName.startsWith("team.member.")) {
            return false;
        }
        return !"team.member.message".equals(sseEventName);
    }

    public static Map<String, Object> teamStartedPayload(
            ExpertDefinition team, String parentRunId, List<TeamMemberDefinition> members) {
        return teamStartedPayload(team, parentRunId, members, null);
    }

    public static Map<String, Object> teamStartedPayload(
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members,
            String teamRuntime) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teamId", team.id());
        payload.put("parentRunId", parentRunId);
        payload.put("memberCount", members.size());
        payload.put("collaboration", team.collaboration() != null ? team.collaboration() : "sequential");
        payload.put("pattern", team.coordinationPattern());
        if (teamRuntime != null && !teamRuntime.isBlank()) {
            payload.put("teamRuntime", teamRuntime);
        }
        TeamLeadDefinition lead = team.lead();
        if (lead != null && team.coordination() != null && team.coordination().hasLead()) {
            Map<String, Object> leadPayload = new LinkedHashMap<>();
            leadPayload.put("name", lead.name());
            leadPayload.put("title", lead.resolvedTitle());
            if (lead.avatar() != null && !lead.avatar().isBlank()) {
                leadPayload.put("avatar", lead.avatar());
            }
            payload.put("lead", leadPayload);
        }
        List<Map<String, Object>> roster = new ArrayList<>();
        for (TeamMemberDefinition member : members) {
            roster.add(memberRosterEntry(member));
        }
        payload.put("members", roster);
        return payload;
    }

    public static Map<String, Object> teamCompletedPayload(
            ExpertDefinition team,
            String parentRunId,
            int memberCount,
            boolean anyMemberFailed,
            boolean timeBudgetExceeded,
            boolean synthesisFailed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teamId", team.id());
        payload.put("parentRunId", parentRunId);
        payload.put("memberCount", memberCount);
        payload.put("anyMemberFailed", anyMemberFailed);
        payload.put("timeBudgetExceeded", timeBudgetExceeded);
        payload.put("synthesisFailed", synthesisFailed);
        return payload;
    }

    public static Map<String, Object> memberStartedPayload(
            String parentRunId, String subRunId, TeamMemberDefinition member) {
        Map<String, Object> payload = memberPayload(parentRunId, subRunId, member);
        payload.put("status", "started");
        return payload;
    }

    public static Map<String, Object> memberCompletedPayload(
            String parentRunId, String subRunId, TeamMemberDefinition member, String summary) {
        return withSummary(memberPayload(parentRunId, subRunId, member), summary);
    }

    /** A previously-paused member picked up new mail and is running again (auto-reawaken). */
    public static Map<String, Object> memberReawakenedPayload(
            String parentRunId, String subRunId, TeamMemberDefinition member) {
        Map<String, Object> payload = memberPayload(parentRunId, subRunId, member);
        payload.put("status", "reawakened");
        return payload;
    }

    /** A member finished its turn and is idle, waiting for further mail. */
    public static Map<String, Object> memberPausedPayload(
            String parentRunId, String subRunId, TeamMemberDefinition member) {
        Map<String, Object> payload = memberPayload(parentRunId, subRunId, member);
        payload.put("status", "paused");
        return payload;
    }

    public static Map<String, Object> memberFailedPayload(
            String parentRunId, String subRunId, TeamMemberDefinition member, String error) {
        Map<String, Object> payload = memberPayload(parentRunId, subRunId, member);
        payload.put("error", truncate(error, SUMMARY_MAX));
        return payload;
    }

    /**
     * User {@code @member} bypass or member↔member mailbox delivery surfaced on the team timeline.
     */
    public static Map<String, Object> memberMessagePayload(
            String parentRunId,
            String from,
            String fromLabel,
            String toToken,
            List<String> delivered,
            String message,
            String summary,
            boolean broadcast) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("surface", "team");
        payload.put("parentRunId", parentRunId);
        payload.put("from", from);
        if (fromLabel != null && !fromLabel.isBlank()) {
            payload.put("fromLabel", fromLabel);
        }
        payload.put("to", broadcast ? "*" : toToken);
        payload.put("delivered", delivered == null ? List.of() : delivered);
        payload.put("kind", TeamMailboxAddressing.USER_SENDER_ID.equals(from) ? "user_bypass" : "message");
        payload.put("message", message == null ? "" : message);
        if (summary != null && !summary.isBlank()) {
            payload.put("summary", truncate(summary, SUMMARY_MAX));
        }
        return payload;
    }

    public static Map<String, Object> teamBuildCompletedPayload(
            String parentRunId, String teamName, String displayName, int memberCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parentRunId", parentRunId);
        if (teamName != null && !teamName.isBlank()) {
            payload.put("teamName", teamName);
        }
        if (displayName != null && !displayName.isBlank()) {
            payload.put("displayName", displayName);
        }
        payload.put("memberCount", memberCount);
        payload.put("status", "created");
        return payload;
    }

    public static Optional<AdaptedTeamEvent> adaptTeamEvent(
            String eventType,
            Map<String, Object> eventPayload,
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members) {
        return adaptTeamEvents(eventType, eventPayload, team, parentRunId, members).stream().findFirst();
    }

    public static List<AdaptedTeamEvent> adaptTeamEvents(
            String eventType,
            Map<String, Object> eventPayload,
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members) {
        if (eventType == null || eventPayload == null) {
            return List.of();
        }
        return switch (eventType) {
            case TeamEvent.CREATED -> adaptTeamBuildCompleted(eventPayload, parentRunId, members.size());
            case TeamEvent.MEMBER_SPAWNED -> List.of(new AdaptedTeamEvent(
                    "team.member.started",
                    memberStartedPayload(
                            parentRunId,
                            subRunId(parentRunId, stringValue(eventPayload, "member_name", "member_id")),
                            findMember(members, stringValue(eventPayload, "member_name", "member_id")))));
            case TeamEvent.MEMBER_STATUS_CHANGED -> adaptMemberStatus(eventPayload, parentRunId, members)
                    .map(List::of)
                    .orElse(List.of());
            case TeamEvent.MEMBER_EXECUTION_CHANGED -> adaptMemberExecution(eventPayload, parentRunId, members)
                    .map(List::of)
                    .orElse(List.of());
            case TeamEvent.WORKSPACE_ARTIFACT_UPDATED -> List.of(new AdaptedTeamEvent(
                    "team.memory",
                    Map.of(
                            "parentRunId", parentRunId,
                            "artifactPath", stringValue(eventPayload, "artifact_path", "path"),
                            "memberId", stringValue(eventPayload, "member_name", "member_id"))));
            default -> List.of();
        };
    }

    private static List<AdaptedTeamEvent> adaptTeamBuildCompleted(
            Map<String, Object> eventPayload, String parentRunId, int memberCount) {
        String teamName = stringValue(eventPayload, "team_name", "teamName");
        String displayName = stringValue(eventPayload, "display_name", "displayName");
        return List.of(new AdaptedTeamEvent(
                "team.build.completed",
                teamBuildCompletedPayload(parentRunId, teamName, displayName, memberCount)));
    }

    private static Optional<AdaptedTeamEvent> adaptMemberExecution(
            Map<String, Object> eventPayload, String parentRunId, List<TeamMemberDefinition> members) {
        String memberId = stringValue(eventPayload, "member_name", "target_id", "member_id");
        if (memberId.isBlank()) {
            return Optional.empty();
        }
        TeamMemberDefinition member = findMember(members, memberId);
        String subRunId = subRunId(parentRunId, memberId);
        String newStatus = stringValue(eventPayload, "new_status", "status").toLowerCase();
        if ("running".equals(newStatus) || "starting".equals(newStatus)) {
            return Optional.of(new AdaptedTeamEvent(
                    "team.member.started", memberStartedPayload(parentRunId, subRunId, member)));
        }
        if ("completed".equals(newStatus)) {
            return Optional.of(new AdaptedTeamEvent(
                    "team.member.completed",
                    memberCompletedPayload(parentRunId, subRunId, member, "")));
        }
        if ("failed".equals(newStatus) || "timed_out".equals(newStatus) || "cancelled".equals(newStatus)) {
            return Optional.of(new AdaptedTeamEvent(
                    "team.member.failed",
                    memberFailedPayload(parentRunId, subRunId, member, newStatus)));
        }
        return Optional.empty();
    }

    private static Optional<AdaptedTeamEvent> adaptMemberStatus(
            Map<String, Object> eventPayload,
            String parentRunId,
            List<TeamMemberDefinition> members) {
        String status = stringValue(eventPayload, "new_status", "status");
        String memberId = stringValue(eventPayload, "member_name", "target_id", "member_id");
        TeamMemberDefinition member = findMember(members, memberId);
        String subRunId = subRunId(parentRunId, memberId);
        if ("completed".equalsIgnoreCase(status) || "done".equalsIgnoreCase(status)) {
            String summary = stringValue(eventPayload, "summary", "message", "output");
            return Optional.of(new AdaptedTeamEvent(
                    "team.member.completed",
                    memberCompletedPayload(parentRunId, subRunId, member, summary)));
        }
        if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
            return Optional.of(new AdaptedTeamEvent(
                    "team.member.failed",
                    memberFailedPayload(parentRunId, subRunId, member, stringValue(eventPayload, "error", "message"))));
        }
        return Optional.empty();
    }

    public static boolean isParallelFanOut(ExpertDefinition team) {
        return CoordinationSpec.AGENT_TEAM.equals(team.coordinationPattern())
                || "parallel".equalsIgnoreCase(team.collaboration());
    }

    public static Map<String, Object> parallelStartedPayload(
            ExpertDefinition team, String parentRunId, List<TeamMemberDefinition> members) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teamId", team.id());
        payload.put("parentRunId", parentRunId);
        payload.put("memberCount", members.size());
        List<Map<String, Object>> roster = new ArrayList<>();
        for (TeamMemberDefinition member : members) {
            roster.add(memberRosterEntry(member));
        }
        payload.put("members", roster);
        return payload;
    }

    private static Map<String, Object> memberPayload(
            String parentRunId, String subRunId, TeamMemberDefinition member) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", member.id());
        payload.put("memberName", member.name());
        payload.put("expertId", member.expertId());
        payload.put("parentRunId", parentRunId);
        payload.put("subRunId", subRunId);
        payload.put("backendType", member.backend().yamlValue());
        if (member.runtime() != null && member.runtime().baseUrl() != null && !member.runtime().baseUrl().isBlank()) {
            payload.put("memberRuntimeUrl", member.runtime().baseUrl());
        }
        if (member.role() != null && !member.role().isBlank()) {
            payload.put("role", member.role());
        }
        if (member.avatar() != null && !member.avatar().isBlank()) {
            payload.put("avatar", member.avatar());
        }
        if (member.participantRole() != null && !member.participantRole().isBlank()) {
            payload.put("participantRole", member.participantRole());
        }
        return payload;
    }

    private static Map<String, Object> memberRosterEntry(TeamMemberDefinition member) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("memberId", member.id());
        entry.put("memberName", member.name());
        entry.put("order", member.order());
        entry.put("backendType", member.backend().yamlValue());
        if (member.expertId() != null && !member.expertId().isBlank()) {
            entry.put("expertId", member.expertId());
        }
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

    private static TeamMemberDefinition findMember(List<TeamMemberDefinition> members, String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException("member id is required for team event adaptation");
        }
        return members.stream()
                .filter(member -> member.id().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown team member: " + memberId));
    }

    private static String subRunId(String parentRunId, String memberId) {
        return TeamAgentSessionBinding.subRunId(parentRunId, memberId);
    }

    private static Map<String, Object> withSummary(Map<String, Object> payload, String summary) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        if (summary != null && !summary.isBlank()) {
            copy.put("summary", truncate(summary.trim(), SUMMARY_MAX));
        }
        return copy;
    }

    @SafeVarargs
    private static String stringValue(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                String text = value.toString();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max) + "…";
    }

    public record AdaptedTeamEvent(String sseEventName, Map<String, Object> payload) {}
}
