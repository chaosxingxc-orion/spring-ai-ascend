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
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamLeadDefinition;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class TeamOrchestratorTest {

    @Mock
    private ExpertRegistry expertRegistry;

    @Mock
    private SessionPersistenceService sessionPersistenceService;

    @Mock
    private AgentRunExecutor agentRunExecutor;

    @Mock
    private GeneratorVerifierOrchestrator generatorVerifierOrchestrator;

    @Mock
    private MessageBusOrchestrator messageBusOrchestrator;

    @Mock
    private SharedStateOrchestrator sharedStateOrchestrator;

    @Mock
    private TeamBlackboardService teamBlackboardService;

    @Mock
    private MemberRunRouter memberRunRouter;

    private TeamOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        TeamOrchestratorTestFixtures.TeamRunSupport support =
                TeamOrchestratorTestFixtures.support(
                        agentRunExecutor, sessionPersistenceService, teamBlackboardService, memberRunRouter);
        orchestrator = new TeamOrchestrator(
                expertRegistry,
                sessionPersistenceService,
                agentRunExecutor,
                generatorVerifierOrchestrator,
                messageBusOrchestrator,
                sharedStateOrchestrator,
                teamBlackboardService,
                memberRunRouter,
                support.eventEmitter(),
                support.answerPublisher(),
                support.memberPayloadFactory());
    }

    @Test
    void runsMembersSequentiallyAndEmitsTeamEvents() {
        UUID sessionId = UUID.randomUUID();
        WorkmateSession session = new WorkmateSession(
                sessionId,
                "team task",
                "/tmp/ws",
                com.huawei.ascend.examples.workmate.session.SessionStatus.CREATED,
                "product-strategy-team",
                PermissionMode.CRAFT);
        ExpertDefinition team = new ExpertDefinition(
                "product-strategy-team",
                "产品策略团队",
                "desc",
                "team",
                "lead prompt",
                null,
                "product",
                List.of("tag"),
                List.of(),
                List.of(
                        new TeamMemberDefinition("prd-writer", "PRD 写手", "prd-writer", "需求", 1, "📝"),
                        new TeamMemberDefinition("fund-analyst", "基金研究助手", "fund-analyst", "分析", 2, "📊")),
                "sequential",
                new TeamLeadDefinition("产品策略团长", "团队负责人", "🧭"),
                com.huawei.ascend.examples.workmate.office.CoordinationSpec.orchestrator(),
                null,
                Map.of());
        when(expertRegistry.requireExpert("product-strategy-team")).thenReturn(team);
        when(teamBlackboardService.initialize(any(), eq("parent-run"), eq("product-strategy-team"), eq("analyze pricing")))
                .thenReturn(new TeamBlackboardService.MemoryUpdate(
                        "team/parent-run/blackboard.md", "init", "write intro", 100, "init", 1L));
        when(teamBlackboardService.read(any(), eq("parent-run"))).thenReturn("blackboard content");
        when(teamBlackboardService.append(any(), eq("parent-run"), any(), any()))
                .thenReturn(new TeamBlackboardService.MemoryUpdate(
                        "team/parent-run/blackboard.md", "member", "summary", 200, "append", 2L));
        when(teamBlackboardService.memoryPayload(eq("parent-run"), any()))
                .thenReturn(java.util.Map.of("path", "team/parent-run/blackboard.md"));
        when(sessionPersistenceService.beginSubRun(eq(sessionId), any(), eq("parent-run"), any(), any()))
                .thenAnswer(invocation -> RunPersistenceContext.forMember(
                        sessionId,
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3)));
        when(agentRunExecutor.execute(any()))
                .thenReturn(new ExecuteOutcome("final synthesis", false, null));
        when(memberRunRouter.executeMember(any(), any()))
                .thenReturn(new ExecuteOutcome("member output", false, null))
                .thenReturn(new ExecuteOutcome("member output 2", false, null));

        RunPersistenceContext parentContext = RunPersistenceContext.forMember(sessionId, "parent-run", null, null);
        SseEmitter emitter = new SseEmitter();
        orchestrator.runTeam(session, "analyze pricing", emitter, new AtomicBoolean(true), "parent-run", parentContext);

        ArgumentCaptor<String> eventNames = ArgumentCaptor.forClass(String.class);
        verify(agentRunExecutor, atLeastOnce()).emit(eq(emitter), any(), eq(parentContext), eventNames.capture(), any());
        assertThat(eventNames.getAllValues())
                .contains(
                        "team.started",
                        "team.memory",
                        "team.member.started",
                        "team.member.completed",
                        "team.lead.synthesizing",
                        "team.completed");
        assertThat(eventNames.getAllValues().stream().filter("team.member.started"::equals).count())
                .isEqualTo(2);
        ArgumentCaptor<java.util.Map<String, Object>> payloads = ArgumentCaptor.forClass(java.util.Map.class);
        verify(agentRunExecutor, atLeastOnce())
                .emit(eq(emitter), any(), eq(parentContext), eq("team.started"), payloads.capture());
        assertThat(payloads.getValue().get("pattern")).isEqualTo("orchestrator");
    }
}
