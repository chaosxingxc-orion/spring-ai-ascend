package com.huawei.ascend.examples.workmate.team;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamLeadDefinition;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Contract: {@link TeamOrchestrator#runTeam} delegates to the topology-specific orchestrator for
 * generator-verifier / message-bus / shared-state, and runs the inline orchestrator path otherwise.
 */
@ExtendWith(MockitoExtension.class)
class TeamOrchestratorTopologyContractTest {

    private static final List<TeamMemberDefinition> MEMBERS = List.of(
            new TeamMemberDefinition("member-a", "Member A", "expert-a", "role-a", 1, "A"),
            new TeamMemberDefinition("member-b", "Member B", "expert-b", "role-b", 2, "B"));

    @Mock
    private ExpertRegistry expertRegistry;

    @Mock
    private SessionPersistenceService sessionPersistenceService;

    @Mock
    private com.huawei.ascend.examples.workmate.agent.AgentRunExecutor agentRunExecutor;

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

    static Stream<Arguments> delegatedTopologies() {
        return Stream.of(
                Arguments.of(CoordinationSpec.GENERATOR_VERIFIER, "generator-verifier"),
                Arguments.of(CoordinationSpec.MESSAGE_BUS, "message-bus"),
                Arguments.of(CoordinationSpec.SHARED_STATE, "shared-state"));
    }

    @ParameterizedTest
    @MethodSource("delegatedTopologies")
    void runTeamDelegatesToSpecializedOrchestrator(String pattern, String teamId) {
        WorkmateSession session = sessionFor(teamId);
        ExpertDefinition team = teamWith(pattern, teamId, "sequential");
        org.mockito.Mockito.when(expertRegistry.requireExpert(teamId)).thenReturn(team);

        RunPersistenceContext parentContext =
                RunPersistenceContext.forMember(session.getId(), "parent-run", null, null);
        orchestrator.runTeam(
                session, "task", new SseEmitter(), new AtomicBoolean(true), "parent-run", parentContext);

        verifyDelegated(pattern);
        verify(teamBlackboardService, never()).initialize(any(), any(), any(), any());
    }

    static Stream<Arguments> inlineTopologies() {
        return Stream.of(
                Arguments.of(CoordinationSpec.ORCHESTRATOR, "orchestrator-team", "sequential"),
                Arguments.of(CoordinationSpec.PIPELINE, "pipeline-team", "sequential"),
                Arguments.of(CoordinationSpec.AGENT_TEAM, "agent-team", "parallel"));
    }

    @ParameterizedTest
    @MethodSource("inlineTopologies")
    void runTeamUsesInlineOrchestratorPath(String pattern, String teamId, String collaboration) {
        WorkmateSession session = sessionFor(teamId);
        ExpertDefinition team = teamWith(pattern, teamId, collaboration);
        org.mockito.Mockito.when(expertRegistry.requireExpert(teamId)).thenReturn(team);
        org.mockito.Mockito.when(teamBlackboardService.initialize(any(), eq("parent-run"), eq(teamId), eq("task")))
                .thenReturn(new TeamBlackboardService.MemoryUpdate(
                        "team/parent-run/blackboard.md", "init", "task", 100, "init", 1L));
        org.mockito.Mockito.when(teamBlackboardService.read(any(), eq("parent-run"))).thenReturn("blackboard");
        org.mockito.Mockito.when(teamBlackboardService.append(any(), eq("parent-run"), any(), any()))
                .thenReturn(new TeamBlackboardService.MemoryUpdate(
                        "team/parent-run/blackboard.md", "member", "summary", 200, "append", 2L));
        org.mockito.Mockito.when(teamBlackboardService.memoryPayload(eq("parent-run"), any()))
                .thenReturn(Map.of("path", "team/parent-run/blackboard.md"));
        org.mockito.Mockito.when(sessionPersistenceService.beginSubRun(
                        eq(session.getId()), any(), eq("parent-run"), any(), any()))
                .thenAnswer(invocation -> RunPersistenceContext.forMember(
                        session.getId(),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3)));
        org.mockito.Mockito.when(agentRunExecutor.execute(any()))
                .thenReturn(new com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome(
                        "final synthesis", false, null));
        org.mockito.Mockito.when(memberRunRouter.executeMember(any(), any()))
                .thenReturn(new com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome(
                        "member output", false, null))
                .thenReturn(new com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome(
                        "member output 2", false, null));

        RunPersistenceContext parentContext =
                RunPersistenceContext.forMember(session.getId(), "parent-run", null, null);
        orchestrator.runTeam(
                session, "task", new SseEmitter(), new AtomicBoolean(true), "parent-run", parentContext);

        verifyNoInteractions(generatorVerifierOrchestrator, messageBusOrchestrator, sharedStateOrchestrator);
        verify(teamBlackboardService).initialize(any(), eq("parent-run"), eq(teamId), eq("task"));
    }

    private void verifyDelegated(String pattern) {
        if (CoordinationSpec.GENERATOR_VERIFIER.equals(pattern)) {
            verify(generatorVerifierOrchestrator).runTeam(any(), any(), any(), any(), any(), any());
            verifyNoInteractions(messageBusOrchestrator, sharedStateOrchestrator);
            return;
        }
        if (CoordinationSpec.MESSAGE_BUS.equals(pattern)) {
            verify(messageBusOrchestrator).runTeam(any(), any(), any(), any(), any(), any());
            verifyNoInteractions(generatorVerifierOrchestrator, sharedStateOrchestrator);
            return;
        }
        verify(sharedStateOrchestrator).runTeam(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(generatorVerifierOrchestrator, messageBusOrchestrator);
    }

    private static WorkmateSession sessionFor(String teamId) {
        return new WorkmateSession(
                UUID.randomUUID(),
                "topology contract",
                "/tmp/ws",
                com.huawei.ascend.examples.workmate.session.SessionStatus.CREATED,
                teamId,
                PermissionMode.CRAFT);
    }

    private static ExpertDefinition teamWith(String pattern, String teamId, String collaboration) {
        CoordinationSpec coordination = new CoordinationSpec(pattern, null);
        TeamLeadDefinition lead = CoordinationSpec.ORCHESTRATOR.equals(pattern)
                        || CoordinationSpec.AGENT_TEAM.equals(pattern)
                ? new TeamLeadDefinition("Lead", "lead", "L")
                : null;
        return new ExpertDefinition(
                teamId,
                "Topology team",
                "desc",
                "team",
                "lead prompt",
                null,
                "product",
                List.of("tag"),
                List.of(),
                MEMBERS,
                collaboration,
                lead,
                coordination,
                null,
                Map.of());
    }
}
