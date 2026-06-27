package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamLeadDefinition;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.team.dto.TeamSnapshotResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamSnapshotServiceTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private SessionPersistenceService sessionPersistenceService;

    @Mock
    private ExpertRegistry expertRegistry;

    @Mock
    private MemberRunRouter memberRunRouter;

    private TeamSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new TeamSnapshotService(sessionService, sessionPersistenceService, expertRegistry, memberRunRouter);
    }

    @Test
    void buildsSnapshotFromDescriptorAndRunEvents() {
        UUID sessionId = UUID.randomUUID();
        WorkmateSession session = new WorkmateSession(
                sessionId,
                "bus session",
                "/tmp/ws",
                com.huawei.ascend.examples.workmate.session.SessionStatus.CREATED,
                "content-bus-team");

        ExpertDefinition writer = new ExpertDefinition(
                "content-writer",
                "Writer",
                "desc",
                "agent",
                "Write concise marketing copy for products.",
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                List.of());

        ExpertDefinition team = new ExpertDefinition(
                "content-bus-team",
                "Bus Team",
                "Message bus team",
                "team",
                "Coordinate via ingress topic.",
                "Write intro",
                "product",
                List.of(),
                List.of(),
                List.of(new TeamMemberDefinition(
                        "content-writer", "文笔佳", "content-writer", "撰写专家", 1, "📝")),
                "parallel",
                new TeamLeadDefinition("lead", Map.of("zh", "负责人"), null),
                new CoordinationSpec(CoordinationSpec.MESSAGE_BUS, null),
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                List.of());

        when(sessionService.requireSession(sessionId)).thenReturn(session);
        when(expertRegistry.requireExpert("content-bus-team")).thenReturn(team);
        when(expertRegistry.findExpert("content-writer")).thenReturn(java.util.Optional.of(writer));
        when(memberRunRouter.usesRemoteMember("content-writer")).thenReturn(false);
        when(sessionPersistenceService.listEventLog(sessionId)).thenReturn(List.of(
                Map.of(
                        "name",
                        "team.bus.subscribed",
                        "data",
                        Map.of("subscriberMemberId", "content-writer", "topics", List.of("ingress"))),
                Map.of(
                        "name",
                        "team.member.started",
                        "data",
                        Map.of("memberId", "content-writer", "remote", true))));

        TeamSnapshotResponse snapshot = service.build(sessionId);

        assertThat(snapshot.teamId()).isEqualTo("content-bus-team");
        assertThat(snapshot.pattern()).isEqualTo("message-bus");
        assertThat(snapshot.source()).isEqualTo("expert-descriptor+run-events");
        assertThat(snapshot.members()).hasSize(1);
        TeamSnapshotResponse.MemberSnapshot member = snapshot.members().getFirst();
        assertThat(member.promptSummary()).contains("marketing copy");
        assertThat(member.backendType()).isEqualTo("remote");
        assertThat(member.status()).isEqualTo("running");
        assertThat(member.subscriptions()).containsExactly("ingress");
    }

    @Test
    void pausedMemberSnapshotReflectsRunEvents() {
        UUID sessionId = UUID.randomUUID();
        WorkmateSession session = new WorkmateSession(
                sessionId,
                "team session",
                "/tmp/ws",
                com.huawei.ascend.examples.workmate.session.SessionStatus.CREATED,
                "gpt-researcher-team");

        ExpertDefinition team = new ExpertDefinition(
                "gpt-researcher-team",
                "Research Team",
                "desc",
                "team",
                "Coordinate research.",
                null,
                null,
                List.of(),
                List.of(),
                List.of(new TeamMemberDefinition(
                        "topic-researcher", "谭溯源", "topic-researcher-expert", "课题研究员", 1, "🧑")),
                "sequential",
                new TeamLeadDefinition("lead", Map.of("zh", "主编"), null),
                new CoordinationSpec(CoordinationSpec.ORCHESTRATOR, null),
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                List.of());

        when(sessionService.requireSession(sessionId)).thenReturn(session);
        when(expertRegistry.requireExpert("gpt-researcher-team")).thenReturn(team);
        when(expertRegistry.findExpert("topic-researcher-expert")).thenReturn(java.util.Optional.empty());
        when(memberRunRouter.usesRemoteMember("topic-researcher-expert")).thenReturn(false);
        when(sessionPersistenceService.listEventLog(sessionId)).thenReturn(List.of(
                Map.of("name", "team.member.started", "data", Map.of("memberId", "topic-researcher")),
                Map.of("name", "team.member.paused", "data", Map.of("memberId", "topic-researcher"))));

        TeamSnapshotResponse snapshot = service.build(sessionId);

        assertThat(snapshot.members().getFirst().status()).isEqualTo("paused");
    }
}
