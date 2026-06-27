package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamLeadDefinition;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.team.dto.TeamSnapshotResponse;
import com.huawei.ascend.examples.workmate.team.dto.TeamSnapshotResponse.MemberSnapshot;
import com.huawei.ascend.examples.workmate.team.dto.TeamSnapshotResponse.TeamLeadSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TeamSnapshotService {

    private static final int PROMPT_SUMMARY_MAX = 240;

    private final SessionService sessionService;
    private final SessionPersistenceService sessionPersistenceService;
    private final ExpertRegistry expertRegistry;
    private final MemberRunRouter memberRunRouter;

    public TeamSnapshotService(
            SessionService sessionService,
            SessionPersistenceService sessionPersistenceService,
            ExpertRegistry expertRegistry,
            MemberRunRouter memberRunRouter) {
        this.sessionService = sessionService;
        this.sessionPersistenceService = sessionPersistenceService;
        this.expertRegistry = expertRegistry;
        this.memberRunRouter = memberRunRouter;
    }

    public TeamSnapshotResponse build(UUID sessionId) {
        WorkmateSession session = sessionService.requireSession(sessionId);
        if (session.getExpertId() == null || session.getExpertId().isBlank()) {
            throw new IllegalArgumentException("Session has no team expert");
        }
        ExpertDefinition team = expertRegistry.requireExpert(session.getExpertId());
        if (!team.isTeam()) {
            throw new IllegalArgumentException("Session expert is not a team: " + team.id());
        }

        Map<String, MemberSnapshotBuilder> builders = new LinkedHashMap<>();
        for (TeamMemberDefinition member : team.members()) {
            builders.put(member.id(), MemberSnapshotBuilder.fromDescriptor(member, memberRunRouter, expertRegistry));
        }

        boolean enrichedFromEvents = enrichFromRunEvents(sessionId, builders);

        CoordinationSpec coordination = team.coordination();
        String pattern = coordination != null ? coordination.pattern() : team.collaboration();
        List<MemberSnapshot> members = team.members().stream()
                .map(member -> builders.get(member.id()).build())
                .toList();

        return new TeamSnapshotResponse(
                team.id(),
                team.name(),
                team.description(),
                pattern,
                team.collaboration(),
                summarize(team.systemPrompt()),
                toLeadSnapshot(team.lead()),
                members,
                enrichedFromEvents ? "expert-descriptor+run-events" : "expert-descriptor");
    }

    private boolean enrichFromRunEvents(UUID sessionId, Map<String, MemberSnapshotBuilder> builders) {
        boolean touched = false;
        for (Map<String, Object> entry : sessionPersistenceService.listEventLog(sessionId)) {
            Object nameObj = entry.get("name");
            if (!(nameObj instanceof String eventName)) {
                continue;
            }
            Object dataObj = entry.get("data");
            if (!(dataObj instanceof Map<?, ?> rawData)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) rawData;
            if ("team.bus.subscribed".equals(eventName)) {
                touched = true;
                String memberId = stringValue(data.get("subscriberMemberId"));
                if (memberId != null && builders.containsKey(memberId)) {
                    builders.get(memberId).mergeSubscriptions(readStringList(data.get("topics")));
                }
            } else if ("team.member.started".equals(eventName) || "team.member.reawakened".equals(eventName)) {
                touched = true;
                String memberId = stringValue(data.get("memberId"));
                if (memberId != null && builders.containsKey(memberId)) {
                    MemberSnapshotBuilder builder = builders.get(memberId);
                    builder.status = "running";
                    if (Boolean.TRUE.equals(data.get("remote"))) {
                        builder.backendType = "remote";
                    }
                }
            } else if ("team.member.paused".equals(eventName)) {
                touched = true;
                String memberId = stringValue(data.get("memberId"));
                if (memberId != null && builders.containsKey(memberId)) {
                    builders.get(memberId).status = "paused";
                }
            } else if ("team.member.completed".equals(eventName)) {
                touched = true;
                String memberId = stringValue(data.get("memberId"));
                if (memberId != null && builders.containsKey(memberId)) {
                    builders.get(memberId).status = "completed";
                }
            } else if ("team.member.failed".equals(eventName)) {
                touched = true;
                String memberId = stringValue(data.get("memberId"));
                if (memberId != null && builders.containsKey(memberId)) {
                    builders.get(memberId).status = "error";
                }
            }
        }
        return touched;
    }

    private static TeamLeadSnapshot toLeadSnapshot(TeamLeadDefinition lead) {
        if (lead == null) {
            return null;
        }
        String title = lead.title() != null && lead.title().containsKey("zh")
                ? lead.title().get("zh")
                : lead.title() != null && !lead.title().isEmpty()
                        ? lead.title().values().iterator().next()
                        : null;
        return new TeamLeadSnapshot(lead.name(), title, lead.avatar());
    }

    private static String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= PROMPT_SUMMARY_MAX) {
            return trimmed;
        }
        return trimmed.substring(0, PROMPT_SUMMARY_MAX - 1) + "…";
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                out.add(item.toString());
            }
        }
        return out;
    }

    private static final class MemberSnapshotBuilder {
        private final String memberId;
        private final String name;
        private final String expertId;
        private final String role;
        private final int order;
        private final String avatar;
        private final String promptSummary;
        private String backendType;
        private String status = "idle";
        private final Set<String> subscriptions = new LinkedHashSet<>();

        private MemberSnapshotBuilder(
                String memberId,
                String name,
                String expertId,
                String role,
                int order,
                String avatar,
                String promptSummary,
                String backendType) {
            this.memberId = memberId;
            this.name = name;
            this.expertId = expertId;
            this.role = role;
            this.order = order;
            this.avatar = avatar;
            this.promptSummary = promptSummary;
            this.backendType = backendType;
        }

        static MemberSnapshotBuilder fromDescriptor(
                TeamMemberDefinition member, MemberRunRouter memberRunRouter, ExpertRegistry expertRegistry) {
            String promptSummary = expertRegistry.findExpert(member.expertId())
                    .map(expert -> summarize(expert.systemPrompt()))
                    .orElse("");
            String backendType = memberRunRouter.usesRemoteMember(member.expertId()) ? "remote" : "local";
            return new MemberSnapshotBuilder(
                    member.id(),
                    member.name(),
                    member.expertId(),
                    member.role(),
                    member.order(),
                    member.avatar(),
                    promptSummary,
                    backendType);
        }

        void mergeSubscriptions(List<String> topics) {
            subscriptions.addAll(topics);
        }

        MemberSnapshot build() {
            return new MemberSnapshot(
                    memberId,
                    name,
                    expertId,
                    role,
                    order,
                    avatar,
                    promptSummary,
                    backendType,
                    List.copyOf(subscriptions),
                    status);
        }
    }
}
