package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.config.WorkmateTopicBusProperties;
import com.huawei.ascend.examples.workmate.spi.topic.WorkmateTopicBusProvider;
import com.huawei.ascend.examples.workmate.spi.topic.ascend.AscendRuntimeTopicBusProvider;
import com.huawei.ascend.examples.workmate.spi.topic.local.LocalInMemoryTopicBusProvider;
import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class MessageBusOrchestratorTest {

    @Mock
    private ExpertRegistry expertRegistry;

    @Mock
    private SessionPersistenceService sessionPersistenceService;

    @Mock
    private AgentRunExecutor agentRunExecutor;

    @Mock
    private MemberRunRouter memberRunRouter;

    @Mock
    private TeamBlackboardService teamBlackboardService;

    private MessageBusOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        WorkmateTopicBusProperties properties = new WorkmateTopicBusProperties();
        WorkmateTopicBusProvider topicBusProvider = new WorkmateTopicBusProvider(
                List.of(new LocalInMemoryTopicBusProvider(), new AscendRuntimeTopicBusProvider()),
                properties);
        TeamOrchestratorTestFixtures.TeamRunSupport support =
                TeamOrchestratorTestFixtures.support(
                        agentRunExecutor, sessionPersistenceService, teamBlackboardService, memberRunRouter);
        orchestrator = new MessageBusOrchestrator(
                expertRegistry,
                sessionPersistenceService,
                memberRunRouter,
                teamBlackboardService,
                topicBusProvider,
                support.eventEmitter(),
                support.answerPublisher(),
                support.memberPayloadFactory());
    }

    @Test
    void publishesBusEventsAndCompletesWithoutLead() {
        UUID sessionId = UUID.randomUUID();
        WorkmateSession session = new WorkmateSession(
                sessionId,
                "bus task",
                "/tmp/ws",
                com.huawei.ascend.examples.workmate.session.SessionStatus.CREATED,
                "content-bus-team",
                PermissionMode.CRAFT);
        ExpertDefinition team = new ExpertDefinition(
                "content-bus-team",
                "内容总线团队",
                "desc",
                "team",
                null,
                null,
                "product",
                List.of(),
                List.of(),
                List.of(
                        new TeamMemberDefinition("content-writer", "撰写", "content-writer", "撰写", 1, "📝"),
                        new TeamMemberDefinition("content-reviewer", "审核", "content-reviewer", "审核", 2, "✅")),
                "parallel",
                null,
                new CoordinationSpec(CoordinationSpec.MESSAGE_BUS, null),
                null,
                Map.of());
        when(expertRegistry.requireExpert("content-bus-team")).thenReturn(team);
        when(teamBlackboardService.initialize(any(), eq("parent-run"), eq("content-bus-team"), eq("brief")))
                .thenReturn(new TeamBlackboardService.MemoryUpdate(
                        "team/parent-run/blackboard.md", "init", "brief", 80, "init", 1L));
        when(teamBlackboardService.append(any(), eq("parent-run"), any(), any()))
                .thenReturn(new TeamBlackboardService.MemoryUpdate(
                        "team/parent-run/blackboard.md", "section", "preview", 120, "append", 2L));
        when(teamBlackboardService.memoryPayload(eq("parent-run"), any()))
                .thenReturn(java.util.Map.of("path", "team/parent-run/blackboard.md"));
        when(sessionPersistenceService.beginSubRun(eq(sessionId), any(), eq("parent-run"), any(), any()))
                .thenAnswer(invocation -> RunPersistenceContext.forMember(
                        sessionId,
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3)));
        when(memberRunRouter.executeMember(any(), any()))
                .thenReturn(new ExecuteOutcome("writer output", false, null))
                .thenReturn(new ExecuteOutcome("reviewer output", false, null));

        RunPersistenceContext parentContext = RunPersistenceContext.forMember(sessionId, "parent-run", null, null);
        SseEmitter emitter = new SseEmitter();
        orchestrator.runTeam(session, "brief", emitter, new AtomicBoolean(true), "parent-run", parentContext);

        ArgumentCaptor<String> eventNames = ArgumentCaptor.forClass(String.class);
        verify(agentRunExecutor, atLeastOnce()).emit(eq(emitter), any(), eq(parentContext), eventNames.capture(), any());
        assertThat(eventNames.getAllValues())
                .contains(
                        "team.started",
                        "team.iteration.started",
                        "team.bus.subscribed",
                        "team.bus.published",
                        "team.member.started",
                        "team.completed",
                        "message.delta")
                .doesNotContain("team.lead.synthesizing");
    }
}
