package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
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
class GeneratorVerifierOrchestratorTest {

    @Mock
    private ExpertRegistry expertRegistry;

    @Mock
    private SessionPersistenceService sessionPersistenceService;

    @Mock
    private AgentRunExecutor agentRunExecutor;

    @Mock
    private TeamBlackboardService teamBlackboardService;

    @Mock
    private MemberRunRouter memberRunRouter;

    private GeneratorVerifierOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        TeamOrchestratorTestFixtures.TeamRunSupport support =
                TeamOrchestratorTestFixtures.support(
                        agentRunExecutor, sessionPersistenceService, teamBlackboardService, memberRunRouter);
        orchestrator = new GeneratorVerifierOrchestrator(
                expertRegistry,
                sessionPersistenceService,
                agentRunExecutor,
                teamBlackboardService,
                support.eventEmitter(),
                support.answerPublisher());
    }

    @Test
    void loopsUntilVerifierAcceptsAndSkipsLeadSynthesis() {
        UUID sessionId = UUID.randomUUID();
        WorkmateSession session = new WorkmateSession(
                sessionId,
                "review task",
                "/tmp/ws",
                com.huawei.ascend.examples.workmate.session.SessionStatus.CREATED,
                "content-review-team",
                PermissionMode.CRAFT);
        CoordinationSpec coordination =
                new CoordinationSpec(
                        CoordinationSpec.GENERATOR_VERIFIER,
                        new CoordinationSpec.Termination(3, null, null, null),
                        "正文 10–200 个汉字");
        ExpertDefinition team = new ExpertDefinition(
                "content-review-team",
                "内容质控团队",
                "desc",
                "team",
                null,
                null,
                "product",
                List.of(),
                List.of(),
                List.of(
                        new TeamMemberDefinition(
                                "content-writer", "撰写", "content-writer", "撰写", 1, "📝", "generator"),
                        new TeamMemberDefinition(
                                "content-reviewer", "审核", "content-reviewer", "审核", 2, "✅", "verifier")),
                "sequential",
                null,
                coordination,
                null,
                Map.of());
        when(expertRegistry.requireExpert("content-review-team")).thenReturn(team);
        when(teamBlackboardService.initialize(any(), eq("parent-run"), eq("content-review-team"), eq("write intro")))
                .thenReturn(new TeamBlackboardService.MemoryUpdate(
                        "team/parent-run/blackboard.md", "init", "write intro", 80, "init", 1L));
        when(teamBlackboardService.readForPrompt(any(), eq("parent-run"), anyInt())).thenReturn("blackboard");
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
        String goodDraft =
                "生成-校验闭环让撰写与质控轮流修订，无团长模式下成员对等协作。"
                        + "团队在迭代上限内自动驳回并返工，直至字数与要点全部达标，从而提升交付一致性与可预期性。"
                        + "用户描述需求后即可获得经质控背书的一段简介。";
        when(agentRunExecutor.execute(any()))
                .thenReturn(new ExecuteOutcome("draft v1", false, null))
                .thenReturn(new ExecuteOutcome(goodDraft, false, null))
                .thenReturn(new ExecuteOutcome("VERIFIED: yes", false, null));

        RunPersistenceContext parentContext = RunPersistenceContext.forMember(sessionId, "parent-run", null, null);
        SseEmitter emitter = new SseEmitter();
        orchestrator.runTeam(session, "write intro", emitter, new AtomicBoolean(true), "parent-run", parentContext);

        ArgumentCaptor<String> eventNames = ArgumentCaptor.forClass(String.class);
        verify(agentRunExecutor, atLeastOnce())
                .emit(eq(emitter), any(), eq(parentContext), eventNames.capture(), any());
        List<String> events = eventNames.getAllValues();
        assertThat(events)
                .contains(
                        "team.started",
                        "team.memory",
                        "team.iteration.started",
                        "team.member.started",
                        "team.member.completed",
                        "team.verify.started",
                        "team.verify.rejected",
                        "team.verify.accepted",
                        "team.completed",
                        "message.delta")
                .doesNotContain("team.lead.synthesizing");
        assertThat(events.stream().filter("team.iteration.started"::equals).count()).isEqualTo(2);

        ArgumentCaptor<Map<String, Object>> startedPayloads = ArgumentCaptor.forClass(Map.class);
        verify(agentRunExecutor, atLeastOnce())
                .emit(eq(emitter), any(), eq(parentContext), eq("team.started"), startedPayloads.capture());
        assertThat(startedPayloads.getValue().get("pattern")).isEqualTo("generator-verifier");
        assertThat(startedPayloads.getValue()).doesNotContainKey("lead");
    }
}
